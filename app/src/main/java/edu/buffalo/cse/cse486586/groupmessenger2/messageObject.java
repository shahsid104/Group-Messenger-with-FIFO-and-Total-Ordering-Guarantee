package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;

/**
 * Created by shahsid104 on 3/13/2017.
 */

public class messageObject implements Serializable, Comparable<messageObject> {
    String message;
    int flag;
    int process_id;
    double sequenceNumber;
    double finalSequenceNumber;
    String objectID;

    public  messageObject()
    {

    }

    public messageObject(messageObject another)
    {
        try {
            this.message = another.message;
            this.flag = another.flag;
            this.sequenceNumber = another.sequenceNumber;
            this.process_id = another.process_id;
            this.finalSequenceNumber = another.finalSequenceNumber;
            this.objectID = another.objectID;
        }catch(Exception e)
        {

        }
    }
    @Override
    public int compareTo(messageObject another) {
        if (this.finalSequenceNumber < another.finalSequenceNumber)
            return -1;

        if (this.finalSequenceNumber == another.finalSequenceNumber)
            return 0;

        return 1;
    }


}
