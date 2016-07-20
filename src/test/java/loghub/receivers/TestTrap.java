package loghub.receivers;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import loghub.Event;
import loghub.LogUtils;
import loghub.Pipeline;
import loghub.Tools;
import loghub.configuration.Properties;

public class TestTrap {

    private static Logger logger;

    @BeforeClass
    static public void configure() throws IOException {
        Tools.configure();
        logger = LogManager.getLogger();
        LogUtils.setLevel(logger, Level.TRACE, "loghub.SmartContext", "loghub.receivers.SnmpTrap", "loghub.Receiver");
        Configurator.setLevel("fr.jrds.SmiExtensions", Level.ERROR);
    }

    @Test()
    public void testone() throws InterruptedException, IOException {
        BlockingQueue<Event> receiver = new ArrayBlockingQueue<>(1);
        SnmpTrap r = new SnmpTrap(receiver, new Pipeline(Collections.emptyList(), "testone", null));
        r.setPort(1162);
        r.configure(new Properties(Collections.emptyMap()));
        List<String> content = r.smartPrint(new OID("1.0.8802.1.1.2.1.1.2.5"));
        Assert.assertEquals(1, content.size());
        Assert.assertEquals("lldpMessageTxHoldMultiplier", content.get(0));
        r.close();
    }

    @Test()
    public void testbig() throws InterruptedException, IOException {
        BlockingQueue<Event> receiver = new ArrayBlockingQueue<>(2);
        SnmpTrap r = new SnmpTrap(receiver, new Pipeline(Collections.emptyList(), "testbig", null));
        r.setPort(1162);
        r.configure(new Properties(Collections.emptyMap()));
        r.start();

        CommandResponderEvent trapEvent = new CommandResponderEvent(new MessageDispatcherImpl(), new DefaultUdpTransportMapping(), new GenericAddress(), 0,0, null, 0, null, null, 0, null );
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID("1.0.8802.1.1.2.1.4.1.1.8.207185300.2.15079"), new OctetString("vnet7")));
        pdu.add(new VariableBinding(new OID("1.0.8802.1.1.2.1.3.7.1.4.2"), new OctetString("eth0")));
        pdu.add(new VariableBinding(new OID("1.0.8802.1.1.2.1.4.1.1.9.207185300.2.15079"), new OctetString("localhost")));
        pdu.add(new VariableBinding(new OID("1.3.6.1.6.3.1.1.4.1.0"), new OctetString("lldpRemTablesChange")));
        trapEvent.setPDU(pdu);

        r.processPdu(trapEvent);
        Event e = receiver.poll();
        logger.debug(e.getClass());
        @SuppressWarnings("unchecked")
        Map<String,?> details = (Map<String, ?>) e.get("lldpRemSysName");
        Assert.assertEquals(3, Array.getLength(details.get("index")));
        Assert.assertEquals("localhost", details.get("value"));
        r.interrupt();

    }
}