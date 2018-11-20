package org.hobbit.awscontroller.SSH;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */


public class SshTunnelsProvider {
    private static Logger LOGGER = LoggerFactory.getLogger(SshTunnelsProvider.class);

    static SshConnector _sshConnector;
    static Function<HSession, String> _defaultOnConnectHanlder;
    static Function<HSession, String> _defaultOnDisconnectHanlder;
    static HSession _sessionToConnect;
    static HSession _connectedSession;

    public static void init(HSession hierarchicalSession, Function<HSession, String> defaultOnConnectHanlder, Function<HSession, String> defaultOnDisconnectHanlder){
        _sshConnector = new SshConnector();
        _sessionToConnect = hierarchicalSession;
        _defaultOnConnectHanlder = defaultOnConnectHanlder;
        _defaultOnDisconnectHanlder = defaultOnDisconnectHanlder;
    }

    public static SshConnector getSshConnector(){
        return _sshConnector;
    }

    public static Boolean isConnected(){
        return (_connectedSession!=null?true:false);
    }


    public static void newSshTunnel(Function<HSession, String> onConnectHandler, Function<HSession, String> onDisconnectConnectHandler){

        if(_connectedSession!=null) {
            if (onConnectHandler != null)
                onConnectHandler.apply(_connectedSession);
            return;
        }

        try {
            _sshConnector.openTunnel(_sessionToConnect, new Function<HSession, String>() {
                @Override
                public String apply(HSession hSession) {
                    _connectedSession = hSession;
                    if (_defaultOnConnectHanlder != null)
                        _defaultOnConnectHanlder.apply(hSession);
                    if (onConnectHandler != null)
                        onConnectHandler.apply(hSession);
                    return null;
                }
            }, new Function<HSession, String>() {
                @Override
                public String apply(HSession hSession) {
                    if(_connectedSession!=null)
                        _connectedSession.getSession().disconnect();

                    if(_defaultOnDisconnectHanlder!=null)
                        _defaultOnDisconnectHanlder.apply(hSession);
                    if(onDisconnectConnectHandler!=null)
                        onDisconnectConnectHandler.apply(hSession);

                    _connectedSession = null;
                    return null;
                }
            });

        } catch (Exception e) {
            LOGGER.error("Cannot open tunnel: {}", e.getLocalizedMessage());
            e.printStackTrace();
//            if(_connectedSession!=null) {
//                if(_defaultOnDisconnectHanlder!=null)
//                    _defaultOnDisconnectHanlder.apply(_connectedSession);
//                if(onDisconnectConnectHandler!=null)
//                    onDisconnectConnectHandler.apply(_connectedSession);
//                _connectedSession = null;
//            }

        }

    }

}
