package loghub;

import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import loghub.zmq.ZMQCheckedException;
import loghub.zmq.ZMQSocketFactory;
import lombok.Getter;

public class ZMQFactory extends ExternalResource {

    private static final Logger logger = LogManager.getLogger();

    @Getter
    private ZMQSocketFactory factory = null;

    private final TemporaryFolder testFolder;
    private final String subFolder;

    public ZMQFactory(TemporaryFolder testFolder, String subFolder) {
        this.testFolder = testFolder;
        this.subFolder = subFolder;
    }

    public ZMQFactory() {
        this.testFolder = null;
        this.subFolder = null;
    }
    
    @Override
    protected void before() throws Throwable {
        if (testFolder != null) {
            testFolder.newFolder(subFolder);
            factory = new ZMQSocketFactory(Paths.get(testFolder.getRoot().getAbsolutePath(), subFolder, "zmqtest.jks").toAbsolutePath());
        } else {
            factory = new ZMQSocketFactory();
        }
    }

    @Override
    protected void after() {
        logger.debug("Terminating ZMQ manager");
        try {
            factory.close();
        } catch (ZMQCheckedException e) {
            logger.error(Helpers.resolveThrowableException(e), e);
        }
        logger.debug("Test finished");
    }

}
