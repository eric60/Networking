package com.company.Project1B;


/**
 * Shared variables:
 * 1. allBuffer so every thread can input their block into the all buffer.
 **/
public class AllPeerStats {
    protected Integer NUM_BLOCKS = 0;
    protected byte[] allBuffer = null;
    protected int cntTotalBytesAdded = 0;

    protected synchronized void setByteSizeFileBuf(int size) {
        if(allBuffer == null) {
            allBuffer = new byte[size];
        }
    }

    protected synchronized void addBufferToAllBuffer(int offsetinAllBuffer, int blockLength, byte[] toAddBuf) {
        for(int i = 0; i < blockLength; i++) {
            allBuffer[offsetinAllBuffer + i] = toAddBuf[i];
            cntTotalBytesAdded++;
        }
    }

    protected synchronized void setNUM_BLOCKS(int numBlocks) {
        if(NUM_BLOCKS  == 0) {
            this.NUM_BLOCKS = numBlocks;
        }
    }

    protected synchronized int getNUM_BLOCKS() {
        return this.NUM_BLOCKS;
    }
}

