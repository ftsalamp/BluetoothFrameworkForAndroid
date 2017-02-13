package grioanpier.auth.users.bluetoothframework;

/**
 * A class that implements a message that it sends via bluetooth. Other than the actual content of the message, it includes
 * additional information regarding the source and target MAC, if it's global (intended for everyone) or private (intended for a single device)
 * and an application code that can be used to determined the type of the message (user defined).
 */

public class BluetoothMessage {

    public Boolean isGlobal=null;
    public String targetMAC=null;
    public String sourceMAC=null;
    public Integer appCode=null;
    public String content =null;

    BluetoothMessage(){}

    public BluetoothMessage(Boolean isGlobal, String targetMAC, String sourceMAC, Integer appCode, String content) {
        this.isGlobal = isGlobal;
        if (targetMAC==null) targetMAC="null";
        this.targetMAC = targetMAC;
        if (sourceMAC==null) sourceMAC="null";
        this.sourceMAC = sourceMAC;
        this.appCode = appCode;
        this.content = content;
    }

    public BluetoothMessage(String message) throws RuntimeException{
        int length;

        /*Extracting isGlobal parameter from the string*/
        String t_isGlobal;
        length = deformat(message);
        t_isGlobal=message.substring(3,length+3);
        message = message.substring(length+3, message.length());
        switch (t_isGlobal) {
            case "true":
                isGlobal = true;
                break;
            case "false":
                isGlobal = false;
                break;
            case "null":
                isGlobal = null;
                break;
            default:
                throw new IllegalArgumentException("Value for isGlobal parameter cannot be " + t_isGlobal + " (message was: " + message + ")");
        }

        /*Extracting targetMAC parameter from the string*/
        String t_targetMAC;
        length = deformat(message);
        t_targetMAC=message.substring(3,length+3);
        message = message.substring(length+3, message.length());
        if (t_targetMAC.equals("null"))
            targetMAC=null;
        else
            targetMAC=t_targetMAC;

        /*Extracting sourceMAC parameter from the string*/
        String t_sourceMAC;
        length = deformat(message);
        t_sourceMAC=message.substring(3,length+3);
        message = message.substring(length+3, message.length());
        if (t_sourceMAC.equals("null"))
            sourceMAC=null;
        else
            sourceMAC=t_sourceMAC;

        /*Extracting appCode parameter from the string*/
        length = deformat(message);
        appCode=Integer.valueOf(message.substring(3,length+3));

        /*Extracting content parameter from the string*/
        message = message.substring(length+3, message.length());
        content = message;
    }

    /**
     * Returns a String equivalent that can be passed to the Constructor to create the Message once again.
     * This method is (mainly) used by the sender.
     * @return a ready-to-send text-formatted message.
     */
    public String getMessage(){
        //TODO String format(null) equals "0" ?
        return format(isGlobal.toString()) +
                format(targetMAC) +
                format(sourceMAC) +
                format(appCode.toString()) +
                content;
    }

    /**
     * Formats the message in the form of [{@param message.length}][{@param message}]. The length of the message should be less than 4 decimals (0-999)
     * For example, "Hello World!" will be formatted to "012Hello World!", where 12 is the length of "Hello World!".
     *
     * @param message The message to format
     * @return The formatted message
     */
    static String format(String message) {
        StringBuilder builder = new StringBuilder();

        if (message==null)
            return String.valueOf(0);

        if (message.length() < 10) {
            // 001,002,...009
            builder.append(0).append(0);
        } else if (message.length() < 100) {
            //010, 050, 099
            builder.append(0);
        } else if (message.length() > 999)
            throw new IllegalArgumentException("The size of the message cannot be bigger than 999 characters!");

        builder.append(message.length());
        builder.append(message);

        return builder.toString();
    }

    /**
     * De-formats a message that's in the form of [message.length][message][rest]. The length of the message can be any String, as long as its size is less than 4 decimals (0-999)
     * and in the form of [001, 002, ..., 010, 011, ..., 999]. The [rest] can by anything, it isn't taken into account.
     * Use the returned integer to calculate the start and end indexes of the code, which will be at index_start=3 and index_end=3+length.
     * <p/>
     * For example,
     * String message = "012Hello World![rest]";
     * int length = deformat(message);
     * String message = message.substring(3,length+3); // message == "Hello World!"
     * String rest = message.substring(length + 3, message.length()); // rest == "BlahBlahBlah"
     *
     * @param message The message to be de-formatted.
     * @return The length of the actual message, which can be retrieved by calling message.substring(3,length+3)
     */
    static int deformat(String message) {
        int int1 = message.charAt(0) - 48;
        int int2 = message.charAt(1) - 48;
        int int3 = message.charAt(2) - 48;
        return ((100 * int1) + (10 * int2) + int3);
    }

    /**
     * @return a visual friendly overview of the bluetooth message
     */
    @Override
    public String toString(){
        return "isGlobal: " + (isGlobal.toString()) + '\n' +
                "target MAC: " + (targetMAC) + '\n' +
                "source MAC: " + (sourceMAC) + '\n' +
                "app Code: " + (appCode.toString()) + '\n' +
                "message content: " + content;
    }

}


