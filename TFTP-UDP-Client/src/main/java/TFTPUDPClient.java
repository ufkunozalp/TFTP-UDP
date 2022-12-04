/**
 *
 * CandNo: 254899
 *
 */

import java.net.*;
import java.io.*;


/**
 * Main class of TFTP-UDP-Client
 */
public class TFTPUDPClient {

    static InetAddress hostname;
    /**
     * @TODO: port value must be the same with the port that the server is running on
     */
    static int port = 2024;
    static int packetLen = 512;
    
    static String heading = "TFTP-UDP> ";
    static boolean isConnected = false;

    /**
     * isInputValid:
     * checks whether the input(s) given by user is valid
     * @param input
     * @return boolean
     */
    public static boolean isInputValid(String input) {
        String[] userInputArr = input.trim().split(" ");
        int inputLen = userInputArr.length;
        String command = userInputArr[0].trim();
        if (inputLen >=3 ||
                (inputLen != 1 && (command.equals("help") || command.equals("quit"))) ||
                (inputLen != 2 && (command.equals("connect") || command.equals("get") || command.equals("send")))) {
            return false;
        }
        return true;
    }

    /**
     * printCommandList:
     * prints a help menu which contains the list of valid commands
     */
    public static void printCommandList() {
        System.out.print("List of commands: \n connect \t connect to TFTP \n get \t\t receive file \n send \t\t send file \n exit \t\t exit TFTP \n help \t\t print command list \n");
    }

    /**
     * getUserArg:
     * getter method for the input (the one specified at argNum index) given by user
     * @param input
     * @param argNum
     * @return String argument
     */
    public static String getUserArg(String input, int argNum){
        return input.split(" ")[argNum].trim();
    }

    public static void main(String[] args) {

        try {
            printCommandList();
            DatagramPacket senderPacket;
            DatagramPacket receiverPacket;
            PacketFactory packetFactory = null;
            DatagramSocket clientSocket = null;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
            byte[] buffer = new byte[packetLen];

            while (true) {
                System.out.print(heading);
                String input = bufferedReader.readLine();

                if (isInputValid(input)) {
                    String command = getUserArg(input, 0).trim();
                    boolean exit = false;

                    switch (command) {
                        case "connect":
                            if (isConnected) {
                                System.out.println("Already connected to the host: " + hostname.getCanonicalHostName());
                                continue;
                            }
                            clientSocket = new DatagramSocket(0);
                            try {
                                String address = getUserArg(input, 1);
                                hostname = InetAddress.getByName(address);
                                if (!hostname.isReachable(5000)) {
                                    System.out.println("Timeout");
                                    continue;
                                }
                            } catch (UnknownHostException e) {
                                System.out.println("Error");
                                continue;
                            }
                            packetFactory = new PacketFactory(hostname, port, packetLen + 4);
                            System.out.println("Connecting to " + hostname.getCanonicalHostName() + " at the port number " + port);
                            heading = "TFTP-UDP@ " + hostname.getCanonicalHostName() + "> ";
                            isConnected = true;
                            break;

                        case "get":
                            if (!isConnected) {
                                System.out.println("Not connected yet");
                                continue;
                            }
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            String filename = getUserArg(input, 1);
                            buffer = new byte[packetLen + 4];

                            try {
                                /** Sending RRQ to server with the filename. **/
                                senderPacket = packetFactory.createRRQPacket(filename);
                                clientSocket.send(senderPacket);
                                clientSocket.setSoTimeout(5000);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Failed to connect to the server");
                                continue;
                            }
                            boolean receivingMessage = true;
                            while (true) {
                                try {
                                    receiverPacket = new DatagramPacket(buffer, buffer.length);
                                    clientSocket.setSoTimeout(5000);
                                    clientSocket.receive(receiverPacket);

                                    byte[] packetData = receiverPacket.getData();
                                    byte[] opCode = packetFactory.getOpCode(packetData);

                                    /**
                                     * receiverPacket can be either DATA or ERROR packet.
                                     */
                                    if (opCode[1] == PacketFactory.errOpCode[1]) {
                                        /**
                                         * If receiver packet is an ERROR packet, prints error message and terminates the program
                                         */
                                        String errorMsg = packetFactory.getErrorMessage(packetData);
                                        System.out.println(errorMsg);
                                        break;
                                    }

                                    if (receiverPacket.getLength() < packetLen + 4 && opCode[1] == PacketFactory.dataOpCode[1]) {
                                        /**
                                         * If receiver packet is DATA packet, transfers the file
                                         */
                                        FileOutputStream filestream = new FileOutputStream(filename);
                                        byte[] fileDataBytes = packetFactory.getDataBytes(packetData);
                                        outputStream.write(fileDataBytes);
                                        filestream.write(outputStream.toByteArray());
                                        filestream.close();

                                        // Sends the last ACK package before termination
                                        byte[] bNum = packetFactory.getBlockNum(packetData);
                                        DatagramPacket sPkt = packetFactory.createACKPacket(bNum, receiverPacket.getPort());
                                        clientSocket.send(sPkt);

                                        System.out.println("File transfer is done.");
                                        break;
                                    }
                                    if (opCode[1] == PacketFactory.dataOpCode[1]) {
                                        if (receivingMessage) {
                                            receivingMessage = false;
                                            System.out.println("Receiving file...");
                                        }
                                        byte[] fileDataBytes = packetFactory.getDataBytes(packetData);
                                        outputStream.write(fileDataBytes);

                                        byte[] blockNum = packetFactory.getBlockNum(packetData);
                                        DatagramPacket sendPacket = packetFactory.createACKPacket(blockNum, receiverPacket.getPort());
                                        clientSocket.send(sendPacket);

                                    }
                                } catch (SocketTimeoutException e) {
                                    System.out.println("Timeout");
                                    break;
                                }
                            }
                            break;
                        case "send":
                            if (!isConnected) {
                                System.out.println("Not connected yet");
                                continue;
                            }
                            filename = getUserArg(input, 1);
                            buffer = new byte[packetLen + 4];
                            ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);

                            try {
                                /** Sending WRQ to server with the filename. **/
                                senderPacket = packetFactory.createWRQPacket(filename);
                                clientSocket.send(senderPacket);
                                clientSocket.setSoTimeout(5000);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("Failed to connect to the server");
                                continue;
                            }
                            receivingMessage = true;
                            while (true) {
                                try {
                                    receiverPacket = new DatagramPacket(buffer, buffer.length);
                                    clientSocket.setSoTimeout(5000);
                                    clientSocket.receive(receiverPacket);

                                    byte[] packetData = receiverPacket.getData();
                                    byte[] opCode = packetFactory.getOpCode(packetData);

                                    /**
                                     * receiverPacket can be either DATA or ERROR packet.
                                     */
                                    if (opCode[1] == PacketFactory.errOpCode[1]) {
                                        /**
                                         * If receiver packet is an ERROR packet, prints error message and terminates the program
                                         */
                                        String errorMsg = packetFactory.getErrorMessage(packetData);
                                        System.out.println(errorMsg);
                                        break;
                                    }

                                    if (receiverPacket.getLength() < packetLen + 4 && opCode[1] == PacketFactory.dataOpCode[1]) {
                                        /**
                                         * If receiver packet is DATA packet, sends the file
                                         */
                                        FileOutputStream filestream = new FileOutputStream(filename);
                                        byte[] fileDataBytes = packetFactory.getDataBytes(packetData);
                                        inputStream.read(fileDataBytes);
                                        filestream.close();

                                        byte[] blockNum = packetFactory.getBlockNum(packetData);
                                        DatagramPacket sendPacket = packetFactory.createACKPacket(blockNum, receiverPacket.getPort());
                                        clientSocket.send(sendPacket);

                                        System.out.println("File transfer is finished.");
                                        break;
                                    }


                                    if (opCode[1] == PacketFactory.ackOpCode[1]) {
                                        if (receivingMessage) {
                                            receivingMessage = false;
                                            System.out.println("Sending file...");
                                        }
                                        byte[] fileDataBytes = packetFactory.getDataBytes(packetData);
                                        inputStream.read(fileDataBytes);

                                        byte[] blockNum = packetFactory.getBlockNum(packetData);
                                        DatagramPacket sendPacket = packetFactory.createACKPacket(blockNum, receiverPacket.getPort());
                                        clientSocket.send(sendPacket);

                                    }
                                } catch (SocketTimeoutException e) {
                                    System.out.println("Timeout");
                                    break;
                                }
                            }
                            break;


                        case "exit":
                            if (!isConnected) {
                                exit = true;
                                System.out.println("Exiting TFTP");
                                break;
                            } else {
                                isConnected = false;
                                heading = "TFTP-UDP>";
                                System.out.println("Disconnecting from the host: " + hostname.getCanonicalHostName());
                            }
                            break;
                        case "help":
                            printCommandList();
                            break;
                        default:
                            System.out.println("Invalid command");
                            continue;
                    }
                    if(exit){
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error");
        }
    }
}
/**
 * Factory Design Pattern Class to create packet objects
 */
class PacketFactory {

    public int packetLen;
    public InetAddress serverAddress;
    public int port;

    public static byte[] rrqOpCode = {0,1};
    public static byte[] wrqOpCode = {0,2};
    public static byte[] dataOpCode = {0,3};
    public static byte[] ackOpCode = {0,4};
    public static byte[] errOpCode = {0,5};

    /**
     * PacketFactory Constructor
     * @param serverAddress
     * @param port
     * @param packetLen
     */
    public PacketFactory(InetAddress serverAddress, int port, int packetLen) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.packetLen = packetLen;
    }

    /**
     * createRRQPacket:
     * creates a DatagramPacket for Read Request
     * @param filenameStr
     * @return Read Request DatagramPacket
     */
    public  DatagramPacket createRRQPacket(String filenameStr){
        byte[] filename = filenameStr.getBytes();
        byte[] octetMode = "octet".getBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(rrqOpCode.length + filename.length + octetMode.length + 2);
        try {
            /**
             *  2 bytes     string    1 byte     string   1 byte
             *  ------------------------------------------------
             * | Opcode |  Filename  |   0  |    Mode    |   0  |
             *  ------------------------------------------------
             */
            outputStream.write(rrqOpCode);
            outputStream.write(filename);
            outputStream.write(0);
            outputStream.write(octetMode);
            outputStream.write(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        DatagramPacket readPacket = new DatagramPacket(outputStream.toByteArray(), outputStream.toByteArray().length, serverAddress, port);
        return readPacket;
    }

    /**
     * createWRQPacket:
     * creates a DatagramPacket for Write Request
     * @param filenameStr
     * @return Write Reqyest DatagramPacket
     */
    public DatagramPacket createWRQPacket(String filenameStr){
        byte[] filename = filenameStr.getBytes();
        byte[] octetMode = "octet".getBytes();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(wrqOpCode.length + filename.length + octetMode.length + 2);
        try {
            /**
             *  2 bytes     string    1 byte     string   1 byte
             *  ------------------------------------------------
             * | Opcode |  Filename  |   0  |    Mode    |   0  |
             *  ------------------------------------------------
             */
            outputStream.write(wrqOpCode);
            outputStream.write(filename);
            outputStream.write(0);
            outputStream.write(octetMode);
            outputStream.write(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new DatagramPacket(outputStream.toByteArray(), outputStream.toByteArray().length, serverAddress, port);

    }

    /**
     * createAckPacket:
     * creates a DatagramPacket for Acknowledgement
     * @param blockNum
     * @param port
     * @return Acknowledgement DatagramPacket
     */
    public  DatagramPacket createACKPacket(byte[] blockNum, int port){
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(ackOpCode.length + blockNum.length);
        try {
            /**
             *   2 bytes     2 bytes
             *  ---------------------
             * | Opcode |   Block #  |
             *  ---------------------
             */
            outputStream.write(ackOpCode);
            outputStream.write(blockNum);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new DatagramPacket(outputStream.toByteArray(), outputStream.toByteArray().length, serverAddress, port);
    }

    /**
     * getBlockNum:
     * getter method for block number
     * @param data
     * @return blockNum
     */
    public byte[] getBlockNum(byte[] data) {
        /**
         *   2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
         */
        byte[] blockNum = new byte[2];
        blockNum[0] = data[2];
        blockNum[1] = data[3];
        return blockNum;
    }

    /**
     * getOpCode:
     * getter method for OpCode
     * @param data
     * @return opCode
     */
    public byte[] getOpCode(byte[] data) {
        /**
         *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------
         */
        byte[] opCode = new byte[2];
        opCode[0] = data[0];
        opCode[1] = data[1];
        return opCode;
    }

    /**
     * getDataBytes
     * getter method for bytes of data packet
     * @param data
     * @return dataBytes
     */
    public byte[] getDataBytes(byte[] data) {
        /**
         *   2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
         */
        // Data Bytes packet is always 512 bytes
        byte[] dataBytes = new byte[packetLen-4];
        for(int i=0; i<data.length-4; i++) {
            dataBytes[i] = data[i+4];
        }
        return dataBytes;
    }

    /**
     * getErrorMessage
     * getter method for error message
     * @param data
     * @return
     */
    public String getErrorMessage(byte[] data) {
        /**
         *  2 bytes     2 bytes      string    1 byte
         *  -----------------------------------------
         * | Opcode |  ErrorCode |   ErrMsg   |   0  |
         *  -----------------------------------------
         */
        byte[] errMsgBytes = new byte[data.length-5];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(errMsgBytes.length);
        for(int i=0; i<data.length-5; i++) {
            errMsgBytes[i] = data[i+4];
        }
        try {
            outputStream.write(errMsgBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toString();
    }

}