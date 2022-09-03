package nextpresso.model;

/**
 * Generic exception that covers any error that happens during the communication with the NextPresso protocol
 * This error may be thrown when a message cannot be sent, read or incorrect data is being found
 */
public class NextPressoException extends Exception{
    public final String title;

    /**
     * Creates a new NextPresso exception
     * @param title The title to display in a GUI title bar
     * @param message Cause of the exception
     */
    public NextPressoException(String title, String message) {
        super(message);
        if(title == null || title.equals("")) this.title = "NextPresso Error";
        else this.title = title;
    }

    @Override
    public String toString() {
        return "NextPresso Error";
    }
}
