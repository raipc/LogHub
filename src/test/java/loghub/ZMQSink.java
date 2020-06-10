package loghub;

import java.io.IOException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.Certificate;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZPoller;

import loghub.zmq.ZMQCheckedException;
import loghub.zmq.ZMQHandler;
import loghub.zmq.ZMQHelper;
import loghub.zmq.ZMQHelper.Method;
import loghub.zmq.ZMQSocketFactory;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true)
public class ZMQSink<M> extends Thread implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger();

    public static class Builder<M> {
        @Setter
        private String source = "tcp://localhost:2120";
        @Setter
        private SocketType type = SocketType.SUB;
        @Setter
        private int hwm = 1000;
        @Setter
        private Method method = Method.BIND;
        @Setter
        private String serverKey = null;
        @Setter
        PrivateKeyEntry keyEntry = null;
        @Setter
        private String security = null;
        @Setter
        private ZMQSocketFactory zmqFactory = null;
        @Setter
        Function<Socket, M> receive = null;
        @Setter
        byte[] topic = null;

        private Builder() {};
        public ZMQSink<M> build() {
            return new ZMQSink<M>(this);
        }
    }

    public static <M> Builder<M> getBuilder() {
        return new Builder<M>();
    }

    public volatile boolean running = false;
    private final ZMQHandler<M> handler;

    private ZMQSink(Builder<M> builder) {
        handler = new ZMQHandler.Builder<M>()
                        .setHwm(builder.hwm)
                        .setSocketUrl(builder.source)
                        .setMethod(builder.method)
                        .setType(builder.type)
                        .setTopic(builder.topic)
                        .setLogger(logger)
                        .setName("ZMQSink")
                        .setReceive(builder.receive)
                        .setMask(ZPoller.IN)
                        .setSecurity(builder.security)
                        .setKeyEntry(builder.keyEntry)
                        .setServerPublicKeyToken(builder.serverKey)
                        .setZfactory(builder.zmqFactory)
                        .setEventCallback(ZMQHelper.getEventLogger(logger))
                        .build();
        setDaemon(true);
        setName("Sink");
        start();
    }

    public Certificate getPublicKey() {
        return handler.getCertificate();
    }

    public void run() {
        try {
            handler.start();
            logger.debug("Sink started");
            while (handler.isRunning()) {
                logger.trace("Sink loop");
                handler.dispatch(null);
            }
        } catch (ZMQCheckedException ex) {
            logger.error("Failed handler dispatch", ex);
        } finally {
            handler.close();
        }
    }

    @Override
    public synchronized void close() throws IOException, ZMQCheckedException {
       handler.stopRunning();
    }

    @Override
    public void interrupt() {
        handler.interrupt(this, super::interrupt);
    }

}
