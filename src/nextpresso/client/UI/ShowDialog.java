package nextpresso.client.UI;

import nextpresso.model.NextPressoException;

import javax.swing.*;
import java.io.File;

/**
 * Sets up and creates various dialog windows
 */
public class ShowDialog {
    /**
     * Show the login dialog
     * @return An array of user inputs (server IP, server port, username, optional password
     */
    public static String[] showLoginDialog() throws NextPressoException {
        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel();
        SpinnerNumberModel spinnerNumberModel2 = new SpinnerNumberModel();
        JTextField serverIP = new JTextField();
        JSpinner messagePort = new JSpinner(spinnerNumberModel);
        JSpinner filePort = new JSpinner(spinnerNumberModel2);
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        spinnerNumberModel.setMaximum(65535);
        spinnerNumberModel.setMinimum(1025);
        spinnerNumberModel.setValue(1337);
        spinnerNumberModel2.setMaximum(65535);
        spinnerNumberModel2.setMinimum(1025);
        spinnerNumberModel2.setValue(7331);
        messagePort.setEditor(new JSpinner.NumberEditor(messagePort,"#"));
        filePort.setEditor(new JSpinner.NumberEditor(filePort,"#"));
        JComponent[] inputs = new JComponent[]{
                new JLabel("Server IP"),
                serverIP,
                new JLabel("Server message port"),
                messagePort,
                new JLabel("Server file port"),
                filePort,
                new JLabel("Username"),
                username,
                new JLabel("Password (optional)"),
                password,
                new JLabel("If you do not enter a password, you will be logged in as an unauthenticated user (without a star)")
        };
        if (JOptionPane.showConfirmDialog(null, inputs, "Connect", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION){
            String[] userInputs = new String[]{serverIP.getText(),messagePort.getValue().toString(),filePort.getValue().toString(),username.getText(), String.valueOf(password.getPassword())};
            validateUsername(userInputs[3]);
            return (userInputs);
        } else return null;
    }

    /**
     * Check username for illegal input
     * @param username String user input for username
     */
    private static void validateUsername(String username) throws NextPressoException {
        if(username.length() < 3) throw new NextPressoException("Invalid Login Input","Username is too short. Needs to be 3 at least");
        if(username.charAt(0) == '*') throw new NextPressoException("Invalid Login Input","Username cannot start with an asterisk!");
        if(username.contains("/") || username.contains("=")) throw new NextPressoException("Invalid Login Input","Username cannot contain '/' or '='!");
        if(username.length() > 15) throw new NextPressoException("Invalid Login Input","Username is too long!"); //To prevent GUI overflow bugs
    }

    /**
     * Show the About dialog that contains app information
     */
    public static void aboutDialog(){JOptionPane.showMessageDialog(null, """
            Mocha, a NextPresso chat client
            Designed for the NPP/1.1 chat protocol
            
            Powered by JAVA and 3-hour classes
            Created by Orphoros and SarenDev
            Copyright 2021
            ""","About Mocha", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Show the dialog to leave or join a group
     * @param groupList List of available groups
     * @param isLeave True - show the leaving option, False - show the joining option
     * @return The selected group name
     */
    public static String leaveJoinDialog(String[] groupList, boolean isLeave){
        JComboBox<String> groupSelector = new JComboBox<>();
        groupSelector.setModel(new DefaultComboBoxModel<>(groupList));
        groupSelector.setEditable(false);
        JComponent[] inputs = new JComponent[]{groupSelector};
        if (JOptionPane.showConfirmDialog(null, inputs, isLeave ? "Leave group" : "Join group", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION){
            return (String) groupSelector.getSelectedItem();
        }
        return null;
    }

    /**
     * Show the dialog window to create a new group
     * @return Group name
     */
    public static String groupCreateDialog() throws NextPressoException {
        JTextField groupname = new JTextField();
        JComponent[] inputs = new JComponent[]{
                new JLabel("Group name"),
                groupname
        };
        if (JOptionPane.showConfirmDialog(null, inputs, "Create group", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION){
            String groupnameInput = groupname.getText();
            validateGroupName(groupnameInput);
            return groupnameInput;
        }
        return null;
    }

    /**
     * Check group name for illegal input
     * @param groupname String user input for groupname
     */
    private static void validateGroupName(String groupname) throws NextPressoException {
        if(groupname.contains("/") || groupname.contains("=")) throw new NextPressoException("Invalid Groupname","Group name cannot contain '/' or '='!");
        if(groupname.length() > 15) throw new NextPressoException("Invalid Groupname","Group name is too long!"); //To prevent GUI overflow bugs
    }

    /**
     * Show the dialog window to upload a new file
     * @return The uploaded file. Returns null if no file was selected
     */
    public static File uploadDialog(){
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION){
            return chooser.getSelectedFile();
        }
        return null;
    }

    /**
     * Show the dialog to inform the user about incoming files
     * @param filename Name of the file
     * @param sender Username of the file sender
     * @return True - the user accepted the file
     */
    public static boolean fileAcceptanceDialog(String filename, String sender){
        return JOptionPane.showConfirmDialog(null,
                new JLabel("Would you like to receive file \""+filename+"\" from user \""+sender+"\"?"), "File dowload", JOptionPane.YES_NO_OPTION) == JOptionPane.OK_OPTION;
    }

    /**
     * Show info dialog window
     * @param message Information to display
     * @param title Title of the dialog window
     */
    public static void infoDialog(String message, String title){JOptionPane.showMessageDialog(null,message,title,JOptionPane.INFORMATION_MESSAGE);}

    /**
     * Show error dialog window
     * @param error Error information to display
     * @param title Name of the error to put in the dialog window's title bar
     */
    public static void errorDialog(String error, String title){JOptionPane.showMessageDialog(null,error,title,JOptionPane.ERROR_MESSAGE);}

    /**
     * Show dynamic error dialog window
     * @param e Exception to handle
     * @param title Title to use
     */
    public static void errorDialog(Exception e, String title){errorDialog(e.getMessage(),title);}

    /**
     * Show a warning dialog
     * @param message Message to show
     * @param title Title of the dialog box
     */
    public static void warningDialog(String message, String title){JOptionPane.showMessageDialog(null,message,title,JOptionPane.WARNING_MESSAGE);}
}
