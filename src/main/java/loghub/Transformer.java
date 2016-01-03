package loghub;

import java.util.Map;

import loghub.configuration.Beans;

@Beans({"threads"})
public abstract class Transformer {

    private int threads = 1;

    public Transformer() {
    }

    public boolean configure(Map<String, Object> properties) {
        return true;
    }

    public abstract void transform(Event event);
    public abstract String getName();

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

}
