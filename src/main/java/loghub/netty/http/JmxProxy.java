package loghub.netty.http;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

public class JmxProxy extends HttpStreaming {

    private static final Logger logger = LogManager.getLogger();

    private final static MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private static final JsonFactory factory = new JsonFactory();
    private static final ThreadLocal<ObjectMapper> json = ThreadLocal.withInitial(() -> new ObjectMapper(factory).configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false));

    private int size;

    @Override
    public boolean acceptRequest(HttpRequest request) {
        String uri = request.uri();
        return uri.startsWith("/jmx");
    }

    @Override
    protected boolean processRequest(FullHttpRequest request, ChannelHandlerContext ctx) throws HttpRequestFailure {
        String name = request.uri().replace("/jmx/", "");
        try {
            name = URLDecoder.decode(name, "UTF-8");
            Set<ObjectName> objectinstance = server.queryNames(new ObjectName(name), null);
            ObjectName found = objectinstance.stream().
                    findAny()
                    .orElseThrow(() -> new HttpRequestFailure(HttpResponseStatus.NOT_FOUND, String.format("malformed object name '%s'", request.uri().replace("/jmx/", ""))));
            MBeanInfo info = server.getMBeanInfo(found);
            MBeanAttributeInfo[] attrInfo = info.getAttributes();
            Map<String, Object> mbeanmap = new HashMap<>(attrInfo.length);
            Arrays.stream(attrInfo)
            .map(i -> i.getName())
            .forEach(i -> {
                try {
                    Object o = resolveAttribute(server.getAttribute(found, i));
                    if (o != null) {
                        mbeanmap.put(i, o);
                    }
                } catch (Exception e) {
                }
            });
            String serialized = json.get().writeValueAsString(mbeanmap);
            ByteBuf content = Unpooled.copiedBuffer(serialized + "\r\n", CharsetUtil.UTF_8);
            size = content.readableBytes();
            return writeResponse(ctx, request, content);
        } catch (MalformedObjectNameException e) {
            throw new HttpRequestFailure(BAD_REQUEST, String.format("malformed object name '%s': %s", name, e.getMessage()));
        } catch (IntrospectionException | ReflectionException | RuntimeException | JsonProcessingException e) {
            Throwable t = e;
            while ( t.getCause() != null) {
                t = t.getCause();
            }
            throw new HttpRequestFailure(BAD_REQUEST, String.format("malformed object content '%s': %s", name, e.getMessage()));
        } catch (InstanceNotFoundException e) {
            throw new HttpRequestFailure(HttpResponseStatus.NOT_FOUND, String.format("malformed object name '%s': %s", name, e.getMessage()));
        } catch (UnsupportedEncodingException e) {
            throw new HttpRequestFailure(HttpResponseStatus.NOT_FOUND, String.format("malformed object name '%s': %s", name, e.getMessage()));
        }

    }

    private Object resolveAttribute(Object o) {
        logger.trace("found a {}", () -> o.getClass().getCanonicalName());
        if(o instanceof CompositeData) {
            CompositeData co = (CompositeData) o;
            Map<String, Object> content = new HashMap<>();
            co.getCompositeType().keySet().forEach( i-> {
                content.put(i, resolveAttribute(co.get(i)));
            });
            return content;
        } else if(o instanceof ObjectName) {
            return null;
        } else if(o instanceof TabularData) {
            TabularData td = (TabularData) o;
            Object[] content = new Object[td.size()];
            AtomicInteger i = new AtomicInteger(0);
            td.values().stream().forEach( j-> content[i.getAndIncrement()] = j);
            return content;
        } else {
            return o;
        }
    }


    @Override
    protected String getContentType() {
        return "application/json; charset=utf-8";
    }

    @Override
    protected int getSize() {
        return size;
    }

    @Override
    protected Date getContentDate() {
        return null;
    }

    @Override
    protected void addCustomHeaders(HttpRequest request, HttpResponse response) {
        response.headers().add(HttpHeaderNames.CACHE_CONTROL, "private, max-age=0");
        response.headers().add(HttpHeaderNames.EXPIRES, "-1");
        super.addCustomHeaders(request, response);
    }

}
