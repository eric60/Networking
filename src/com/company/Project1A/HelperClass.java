package com.company.Project1A;

import java.io.UnsupportedEncodingException;
import java.util.Scanner;

public class HelperClass {

    public static int[] parseMessage(byte[] input) throws UnsupportedEncodingException {
        // Parse the status message
        String str = new String(input, "UTF-8");
        System.out.println(str);
        if (!str.contains("200 OK")) {
            System.out.println("Status not okay");
        }

        Scanner scanner = new Scanner(str);
        scanner.nextLine(); // Skip status line

        String strOffset = scanner.nextLine().trim();
        Integer byte_offset = Integer.parseInt(strOffset.substring(26));


        String strLength = scanner.nextLine().trim();
        Integer byte_length = Integer.parseInt(strLength.substring(18));

        return new int[] {byte_offset, byte_length};
    }

    // Method returns an array with the 4 byte XOR checksum answer with a 1 byte \n char for a total of 5 bytes
    // Ex: b0 b1 b2 b3|b4 b5 b6 b7|b8 b9 b10|
    public static byte[] computeXORChecksum(byte[] bytes) {
        if(bytes.length <= 4) {
            return bytes;
        }
        byte[] checksum = new byte[5];
        for(int i = 0; i < 4; i++) {
            checksum[i] = bytes[i];
            for(int j = i + 4; j < bytes.length; j+=4) {
                checksum[i] = (byte) (checksum[i] ^ bytes[j]);
            }
        }
        checksum[4] = (byte) '\n';
        return checksum;
    }
}
