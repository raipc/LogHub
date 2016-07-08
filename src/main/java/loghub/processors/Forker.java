package loghub.processors;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import loghub.Event;
import loghub.Pipeline;
import loghub.Processor;
import loghub.configuration.Properties;

/**
 * An empty processor, it's just a place holder. It should never be used directly
 * 
 * @author Fabrice Bacchella
 *
 */
public class Forker extends Processor {

    private static final Logger logger = LogManager.getLogger();

    private String destination;
    private Pipeline pipeDestination;
    private BlockingQueue<Event> mainQueue;

    @Override
    public void process(Event event) {
        throw new UnsupportedOperationException("can't process wrapped event");
    }

    @Override
    public String getName() {
        return null;
    }

    public void fork(Event event) {
        Event newEvent = event.duplicate();
        if(newEvent == null) {
            return;
        }

        newEvent.inject(pipeDestination, mainQueue);

    }

    /**
     * @return the destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @param destination the destination to set
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    @Override
    public boolean configure(Properties properties) {
        if( ! properties.namedPipeLine.containsKey(destination)) {
            logger.error("invalid destination for forked event: {}", destination);
            return false;
        }
        pipeDestination = properties.namedPipeLine.get(destination);
        mainQueue = properties.mainQueue;
        return super.configure(properties);
    }

}
