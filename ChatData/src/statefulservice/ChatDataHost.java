package statefulservice;

import java.time.Duration;
import java.util.logging.Logger;
import java.util.logging.Level;

import microsoft.servicefabric.services.runtime.ServiceRuntime;

public class ChatDataHost {

    private static final Logger logger = Logger.getLogger(ChatDataHost.class.getName());

    public static void main(String[] args) throws Exception{
        try {
            ServiceRuntime.registerStatefulServiceAsync("ChatDataType", (context)-> new ChatData(context), Duration.ofSeconds(10));
            logger.log(Level.INFO, "Registered stateful service of type ChatDataType");
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception occurred", ex);
            throw ex;
        }
    }
}
