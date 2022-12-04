/**
 *
 * CandNo: 254899
 *
 */


package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class TFTPUDPServer {
    /**
     * @TODO: Determine the server port here
     */
    public static final int port = 2024;
    public static final int buffersize = 516;
    /**
     * readdir: RRQ directory
     * writedir: WRQ directory
     * @TODO: Change "readdir" and "writedir" before running the server (set to my local computer by default :) )
     */
	public static final String readdir = "C:/Users/ufkun/Desktop/";
	public static final String writedir = "C:/Users/ufkun/Desktop/tobewritten/";
    public static final short rrqOpCode = 1;
    public static final short wrqOpCode = 2;
    public static final short dataOpCode = 3;
    public static final short ackOpCode = 4;
    public static final short errOpCode = 5;
    public static final short errCode = 0;

    public static void main(String[] args) {
        try {
            byte[] buffer = new byte[buffersize];
            DatagramSocket socket = new DatagramSocket(null);
            SocketAddress bindPoint = new InetSocketAddress(port);
            socket.bind(bindPoint);
            System.out.printf("Waiting for new requests at port: "+port+"\n");

            while(true) {
                InetSocketAddress clientAddress = getClient(socket, buffer);

                StringBuffer requestedFile = new StringBuffer();
                int reqtype = parseRequest(buffer, requestedFile);

                new Thread(() -> {
                    try {
                        DatagramSocket sendSocket = new DatagramSocket(0);
                        sendSocket.connect(clientAddress);

                        switch (reqtype) {
                            case rrqOpCode:
                                System.out.println("Reading is requested for "+ requestedFile +" from client "+clientAddress.getHostName()+" at port "+clientAddress.getPort());
                                requestedFile.insert(0, readdir);
                                requestHandler(sendSocket, requestedFile.toString(), rrqOpCode);
                                break;
                            case wrqOpCode:
                                System.out.println("Writing is requested for "+ requestedFile +" from client "+clientAddress.getHostName()+" at port "+clientAddress.getPort());
                                requestedFile.insert(0, writedir);
                                requestHandler(sendSocket, requestedFile.toString(), wrqOpCode);
                                break;
                        }
                        sendSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * getClient
     * Getter method for client
     * @param socket
     * @param buffer
     * @return client
     */
    private static InetSocketAddress getClient(DatagramSocket socket, byte[] buffer) {

        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
        InetSocketAddress client = new InetSocketAddress(receivePacket.getAddress(),receivePacket.getPort());
        return client;
    }
    /**
     * getBlockNumAckPack:
     * Getter method for the block number of ACK packet
     * @param ack: Received ACK DatagramPacket
     * @return blockNum
     */
    public static short getBlockNumAckPack(DatagramPacket ack) {
        /**
         *  2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
         */
        ByteBuffer buffer = ByteBuffer.wrap(ack.getData());
        short opcode = buffer.getShort();
        if (opcode == errOpCode) {
            System.err.println("Error");
            parseErr(buffer);
            return -1;
        }
        short blockNum = buffer.getShort();
        return blockNum;
    }

    /**
     * getBlockNumDataPack:
     * Getter method for the block number of the data packet
     * @param data: Received DATA DatagramPacket
     * @return blockNum
     */
    public static short getBlockNumDataPack(DatagramPacket data) {
        /**
         *   2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
         */
        ByteBuffer buffer = ByteBuffer.wrap(data.getData());
        short opcode = buffer.getShort();
        if (opcode == errOpCode) {
            System.out.println("Error");
            parseErr(buffer);
            return -1;
        }
        short blockNum = buffer.getShort();
        return blockNum;
    }

    /**
     * createAckPacket
     * Creates an acknowledgement packet
     * @param blockNum
     * @return ACK Packet which will be sent
     */
    private static DatagramPacket createAckPacket(short blockNum) {
        /**
         *   2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
         */
        ByteBuffer buffer = ByteBuffer.allocate(buffersize);
        buffer.putShort(ackOpCode);
        buffer.putShort(blockNum);

        return new DatagramPacket(buffer.array(), 4);
    }

    /**
     * createDataPacket:
     * Creates a data packet
     * @param blockNum
     * @param data: data which will be sent
     * @param len: length of data
     * @return DATA packet which will be sent
     */
    private static DatagramPacket createDataPacket(short blockNum, byte[] data, int len) {
        /**
         *  2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
         *
         */
        ByteBuffer buffer = ByteBuffer.allocate(buffersize);
        buffer.putShort(dataOpCode);
        buffer.putShort(blockNum);
        buffer.put(data, 0, len);
        return new DatagramPacket(buffer.array(), 4+len);
    }
    /**
     * requestHandler:
     * Handler for requests
     * @param sendSocket
     * @param filepath
     * @param opRQ
     */
    private static void requestHandler(DatagramSocket sendSocket, String filepath, int opRQ) {
        /**
         *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------
         */
        System.out.println(filepath);
        File file = new File(filepath);
        byte[] buf = new byte[buffersize -4];

        switch (opRQ) {
            case rrqOpCode:
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    System.err.println("File not found. Sending error packet.");
                    createAndSendErrPacket(sendSocket, errCode, "");
                    return;
                }
                short blockNum = 1;
                while (true) {
                    int length;
                    try {
                        length = in.read(buf);
                    } catch (IOException e) {
                        System.err.println("Error reading file.");
                        return;
                    }
                    if (length == -1) {
                        length = 0;
                    }
                    DatagramPacket sender = createDataPacket(blockNum, buf, length);
                    System.out.println("Sending the block...");
                    if (sendData(sendSocket, sender, blockNum++)) {
                        System.out.println("Block has successfully sent. Sending another block. Block #: " + blockNum);
                    } else {
                        System.err.println("Error. Lost connection.");
                        createAndSendErrPacket(sendSocket, errCode, "Lost connection.");
                        return;
                    }
                    if (length < 512) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            System.err.println("Error");
                        }
                        break;
                    }
                }
                break;
            case wrqOpCode:
                FileOutputStream output = null;
                try {
                    output = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return;
                }
                blockNum = 0;
                while (true) {
                    DatagramPacket dataPacket = sendAck(sendSocket, createAckPacket(blockNum++), blockNum);
                    if (dataPacket != null) {
                        byte[] data = dataPacket.getData();
                        try {
                            output.write(data, 4, dataPacket.getLength() - 4);
                            System.out.println(dataPacket.getLength());
                        } catch (IOException e) {
                            System.err.println("Error");
                        }
                        if (dataPacket.getLength()-4 < 512) {
                            try {
                                sendSocket.send(createAckPacket(blockNum));
                            } catch (IOException e1) {
                                try {
                                    sendSocket.send(createAckPacket(blockNum));
                                } catch (IOException e) {
                                }
                            }
                            System.out.println("File has written");
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    } else {
                        System.out.println("Client connection lost");
                        try {
                            output.close();
                        } catch (IOException e) {
                            System.err.println("Error");
                        }
                        break;
                    }
                }
                break;
            default:
                System.err.println("Error");
                break;
        }
    }

    /**
     * sendAck:
     * Handler for sending ACK and receiving DATA packets
     * @param sendSocket
     * @param sendingAck
     * @param blockNum
     * @return DATA Packet
     */
    private static DatagramPacket sendAck(DatagramSocket sendSocket, DatagramPacket sendingAck, short blockNum) {
        /**
         *  2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
         *
         */
        byte[] receive = new byte[buffersize];
        DatagramPacket receiver = new DatagramPacket(receive, receive.length);

        while(true) {
            try {
                System.out.println("ACK is sending...");
                sendSocket.send(sendingAck);
                sendSocket.setSoTimeout(5000);
                sendSocket.receive(receiver);

                short block = getBlockNumDataPack(receiver);
                System.out.println(block + " " + blockNum);
                if (block == blockNum) {
                    return receiver;
                } else if (block == -1) {
                    return null;
                } else {
                    System.out.println("Error");
                    throw new SocketTimeoutException();
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout");
                e.printStackTrace();
                try {
                    sendSocket.send(sendingAck);
                } catch (IOException e2) {
                    System.out.println("Error");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                System.out.println("Error");
                e.printStackTrace();
            } finally {
                try {
                    sendSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    System.out.println("Error");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * sendData:
     * Handler for sending DATA and receiving ACK Packets
     * @param sendSocket
     * @param sendingPacket
     * @param blockNum
     * @return boolean
     */
    private static boolean sendData(DatagramSocket sendSocket, DatagramPacket sendingPacket, short blockNum) {
        /**
         *   2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
         */
        byte[] receive = new byte[buffersize];
        DatagramPacket receiver = new DatagramPacket(receive, receive.length);

        while(true) {
            try {
                sendSocket.send(sendingPacket);
                System.out.println("Sent");
                sendSocket.setSoTimeout(5000);
                sendSocket.receive(receiver);

                short ack = getBlockNumAckPack(receiver);

                if (ack == blockNum) return true;
                if (ack == -1) {
                    return false;
                } else {
                    throw new SocketTimeoutException();
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Timeout");
            } catch (SocketException e) {
                System.out.println("Error");
            } catch (IOException e) {
                System.out.println("Error");
            } finally {
                try {
                    sendSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    System.out.println("Error");
                }
            }
        }
    }




    /**
     * createAndSendErrPacket:
     * Creates and sends an error packet
     * @param sendSocket
     * @param errorCode
     * @param errMsg
     */
    private static void createAndSendErrPacket(DatagramSocket sendSocket, short errorCode, String errMsg) {
        /**
         *  2 bytes     2 bytes      string    1 byte
         *  -----------------------------------------
         * | Opcode |  ErrorCode |   ErrMsg   |   0  |
         * -----------------------------------------
         */
        ByteBuffer wrap = ByteBuffer.allocate(buffersize);
        wrap.putShort(errOpCode);
        wrap.putShort(errorCode);
        wrap.put(errMsg.getBytes());
        wrap.put((byte) 0);
        DatagramPacket receivePacket = new DatagramPacket(wrap.array(),wrap.array().length);
        try {
            sendSocket.send(receivePacket);
        } catch (IOException e) {
            System.out.println("Error. Sending error packet.");
            e.printStackTrace();
        }
    }

    /**
     * parseErr:
     * Parses error
     * @param buffer
     */
    private static void parseErr(ByteBuffer buffer) {
        /**
         *  2 bytes     2 bytes      string    1 byte
         *  -----------------------------------------
         * | Opcode |  ErrorCode |   ErrMsg   |   0  |
         * -----------------------------------------
         */
        short errCode = buffer.getShort();
        byte[] buff = buffer.array();
        for (int i=4; i < buff.length; i++) {
            if (buff[i] == 0) {
                String errMsg = new String(buff, 4, i-4);
                System.out.println("Error code "+ errCode + ": " + errMsg);
                break;
            }
        }
    }

    /**
     * parseRequest:
     * Parser to get OP Code of the request
     * @param buffer received request
     * @param requestedFile
     * @return opCode
     */
    private static short parseRequest(byte[] buffer, StringBuffer requestedFile) {
        /**
         *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------
         */
        ByteBuffer wrap = ByteBuffer.wrap(buffer);
        short opCode = wrap.getShort();
        int del = -1;
        for (int i = 2; i < buffer.length; i++) {
            if (buffer[i] == 0) {
                del = i;
                break;
            }
        }
        String fileName = new String(buffer, 2, del-2);
        requestedFile.append(fileName);
        return opCode;
    }
}