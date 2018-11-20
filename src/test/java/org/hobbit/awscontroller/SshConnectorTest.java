package org.hobbit.awscontroller;

import org.hobbit.awscontroller.SSH.HSession;
import org.hobbit.awscontroller.SSH.SshConnector;
import org.junit.Ignore;
import org.junit.Test;

public class SshConnectorTest {

    @Ignore
    @Test
    public void checkAliveConnections() throws Exception {

        String user = "ec2-user";
        String host = "";
        String user2 = "ubuntu";

        String host2 = "10.0.19.167";
        String keyfilepath = "/hobbit.pem";

        SshConnector sshConnector = new SshConnector();

        HSession bastionSession = new HSession(user, host, 22, keyfilepath);
        HSession targetSession = new HSession(user2, host2, 22, keyfilepath, new String[]{ "80" }, bastionSession);

        sshConnector.openTunnel(targetSession);
        int i=0;
        while (i<60) {
            Thread.sleep(3000);
            i++;
        }

    }


}
