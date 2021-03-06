package loghub.processors;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import loghub.BeanChecks;
import loghub.Event;
import loghub.LogUtils;
import loghub.ProcessorException;
import loghub.Tools;
import loghub.configuration.Properties;

public class TestParseJson {

    private static Logger logger;

    @BeforeClass
    static public void configure() throws IOException {
        Tools.configure();
        logger = LogManager.getLogger();
        LogUtils.setLevel(logger, Level.TRACE, "loghub.processors");
    }

    @Test
    public void testSuccess() throws ProcessorException {
        ParseJson parse = new ParseJson();
        parse.setField(new String[] {"message"});
        parse.setAtPrefix("|");
        Assert.assertTrue(parse.configure(new Properties(Collections.emptyMap())));
        Event event = Tools.getEvent();
        event.put("message", "{\"@a\": 1, \"b\": \"value\", \"c\": true, \"d\": [], \"e\": {}}");
        parse.process(event);
        Assert.assertEquals(1, event.get("|a"));
        Assert.assertEquals("value", event.get("b"));
        Assert.assertEquals(true, event.get("c"));
        Assert.assertTrue(event.get("d") instanceof List);
        Assert.assertTrue(event.get("e") instanceof Map);
    }

    @Test(expected=ProcessorException.class)
    public void testFailure() throws ProcessorException {
        ParseJson parse = new ParseJson();
        parse.setField(new String[] {"message"});
        Assert.assertTrue(parse.configure(new Properties(Collections.emptyMap())));
        Event event = Tools.getEvent();
        event.put("message", "{");
        parse.process(event);
    }

    @Test
    public void test_loghub_processors_ParseJson() throws ClassNotFoundException, IntrospectionException {
        BeanChecks.beansCheck(logger, "loghub.processors.ParseJson"
                              ,BeanChecks.BeanInfo.build("atPrefix", String.class)
                        );
    }

}
