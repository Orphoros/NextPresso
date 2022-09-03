package nextpresso.client.core;

import java.util.HashMap;

public class Helpers {

    public static HashMap<String,Boolean> getMessageKeyValuePairs(String NPPMessage){
        HashMap<String,Boolean> keyValuePair = new HashMap<>();
        boolean startCopy = false;
        boolean useValue = false;
        StringBuilder bufferKey = new StringBuilder();
        StringBuilder bufferValue = new StringBuilder();
        for(int i = 0; i < NPPMessage.length(); i ++){
            if(NPPMessage.charAt(i) == '{') {
                startCopy = true;
                useValue = false;
                continue;
            }
            if(NPPMessage.charAt(i) == '}') {
                startCopy = false;
                keyValuePair.put(bufferKey.toString(), bufferValue.toString().equals("1"));
                bufferKey = new StringBuilder();
                bufferValue = new StringBuilder();
                continue;
            }
            if(NPPMessage.charAt(i) == ',' && startCopy) {
                useValue = true;
                continue;
            }
            if(startCopy && !useValue) {
                bufferKey.append(NPPMessage.charAt(i));
                continue;
            }
            if(startCopy) {
                bufferValue.append(NPPMessage.charAt(i));
            }
        }
        return keyValuePair;
    }

}
