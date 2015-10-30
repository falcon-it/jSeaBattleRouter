/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jseabattlerouter;

import java.util.*;
import java.nio.channels.*;
import java.nio.*;
import java.net.*;
import java.nio.charset.StandardCharsets;


public class JSeaBattleRouter {
    
    private static final class BattleConnection {
        private SocketChannel m_c1, m_c2 = null;
        private String m_StartMessage;
        
        public BattleConnection(SocketChannel c1, String msg) {
            m_c1 = c1;
            m_StartMessage = msg;
        }
        
        public final void addClient2(SocketChannel c2, String msg) {
            if(m_c2 == null) {
                m_c2 = c2;
                redirectMessage(m_c1, m_StartMessage);
                redirectMessage(m_c2, msg);
            }
        }
        
        public final boolean isDuplex() {
            return (m_c2 != null);
        }
        
        private static final boolean compareChannel(SocketChannel ch1, SocketChannel ch2) {
            boolean _result = false;
            
            try {
                if(ch1.equals(ch2)) {
                    _result = true;
                }
                else {
                    Socket _s1 = ch1.socket(),
                            _s2 = ch2.socket();
                    _result = ((_s1.getPort() == _s2.getPort()) && 
                            (_s1.getInetAddress().getHostAddress().compareTo(
                                    _s2.getInetAddress().getHostAddress()) == 0));
                }
            }
            catch(Exception e) {
                
            }
            
            return _result;
        }
        
        public final boolean redirectMessage(SocketChannel cc, String msg) {
            boolean _result = false;
            
            if(isDuplex()) {
                SocketChannel _dest = null;
                if(BattleConnection.compareChannel(cc, m_c1)) {
                    _dest = m_c2;
                }
                else {
                    if(BattleConnection.compareChannel(cc, m_c2)) {
                        _dest = m_c1;
                    }
                }
                
                if(_dest != null) {
                    try {
                        _dest.socket().getOutputStream().write(
                                msg.getBytes(StandardCharsets.UTF_8));
                        _result = true;
                    }
                    catch(Exception e) {
                        
                    }
                }
            }
            
            return _result;
        }
        
        public final boolean isThisConnectionChanel(SocketChannel cc) {
            return (BattleConnection.compareChannel(cc, m_c1) || 
                    BattleConnection.compareChannel(cc, m_c2));
        }
        
        private static final void destroyChannel(Selector sel, SocketChannel ch) {
            SelectionKey _sk = ch.keyFor(sel);
            
            if(_sk != null) {
                _sk.cancel();
                
                try {
                    ch.socket().close();
                }
                catch(Exception e) {
                    
                }
            }
        }
        
        public final void destroy(Selector sel) {
            if(m_c1 != null) {
                BattleConnection.destroyChannel(sel, m_c1);
                m_c1 = null;
            }

            if(m_c2 != null) {
                BattleConnection.destroyChannel(sel, m_c2);
                m_c2 = null;
            }
            
            m_StartMessage = null;
        }
    }

    private static final ByteBuffer readBuffer;
    private static final String idFindPattern = "##ID#";
    private static final HashMap<Integer, BattleConnection> connections;
    private static boolean logging = false;
    
    static {
        readBuffer = ByteBuffer.allocate(16384);
        connections = new HashMap();
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            int ports_listeners[] = null;

            if(args != null) {
                for(String arg : args) {
                    String _arg_l = arg.toLowerCase();

                    if(_arg_l.indexOf("ports") == 0) {
                        _arg_l = _arg_l.substring("ports".length() + 1);
                        String _ports_s[] = _arg_l.split(",");
                        ports_listeners = new int[_ports_s.length];
                        for(int i = 0; i < _ports_s.length; ++i) {
                            ports_listeners[i] = Integer.parseInt(_ports_s[i]);
                            
                            if(ports_listeners[i] < 1) {
                                throw new Exception("port fail value");
                            }
                        }
                    }
                    
                    if(_arg_l.indexOf("logging") == 0) {
                        if(_arg_l.substring(
                                "logging".length() + 1).compareTo(
                                        "on") == 0) {
                            JSeaBattleRouter.logging = true;
                        }
                    }
                }
            }
            
            if((ports_listeners == null) ||
                    (ports_listeners.length == 0)) {
                throw new Exception("[ports] not found values");
            }
            
            Selector selector = Selector.open();
            
            for(int _listening_port : ports_listeners) {
                ServerSocketChannel _new_ssc = ServerSocketChannel.open();
                _new_ssc.configureBlocking(false);
                ServerSocket _new_ss = _new_ssc.socket();
                InetSocketAddress _isa = new InetSocketAddress(_listening_port);
                _new_ss.bind(_isa);
                _new_ssc.register(selector, SelectionKey.OP_ACCEPT);
                if(JSeaBattleRouter.logging) {
                    System.out.println(
                            String.format(
                                    "Listening on port %1$s\n", 
                                    _listening_port ));
                }
            }
            
            while(true) {
                int num = selector.select();
                if(num == 0) {
                    continue;
                }
                
                Set<SelectionKey> _keys = selector.selectedKeys();
                Iterator<SelectionKey> _it = _keys.iterator();
                while(_it.hasNext()) {
                    SelectionKey _sel_key = _it.next();
                    
                    if((_sel_key.readyOps() & SelectionKey.OP_ACCEPT) == 
                            SelectionKey.OP_ACCEPT) {
                        try {
                            ServerSocketChannel _sel_ss = 
                                    (ServerSocketChannel)_sel_key.channel();
                            SocketChannel _acc_s = _sel_ss.accept();
                            if(JSeaBattleRouter.logging) {
                                System.out.println(
                                        String.format(
                                                "Accept client %1$s:%2$s\n", 
                                                _acc_s.socket().getInetAddress().getHostAddress(), 
                                                _acc_s.socket().getLocalPort()));
                            }
                            _acc_s.configureBlocking(false);
                            _acc_s.register(selector, SelectionKey.OP_READ);
                        }
                        catch(Exception e) {
                            if(JSeaBattleRouter.logging) {
                                System.err.println(e);
                            }
                        }
                    }
                    
                    if((_sel_key.readyOps() & SelectionKey.OP_READ) == 
                            SelectionKey.OP_READ) {
                        try {
                            SocketChannel _sel_sc = 
                                    (SocketChannel)_sel_key.channel();
                            JSeaBattleRouter.readBuffer.clear();
                            _sel_sc.read(JSeaBattleRouter.readBuffer);
                            JSeaBattleRouter.readBuffer.flip();
                            
                            if(JSeaBattleRouter.readBuffer.limit() == 0) {
                                if(JSeaBattleRouter.logging) {
                                    System.out.println(
                                            String.format(
                                                    "Disconnect client %1$s:%2$s\n", 
                                                    _sel_sc.socket().getInetAddress().getHostAddress(), 
                                                    _sel_sc.socket().getLocalPort()));
                                }
                                
                                for(int _ck : JSeaBattleRouter.connections.keySet()) {
                                    BattleConnection _bf = 
                                            JSeaBattleRouter.connections.get(_ck);
                                    if(_bf.isThisConnectionChanel(_sel_sc)) {
                                        _bf.destroy(selector);
                                        JSeaBattleRouter.connections.remove(_ck);
                                        break;
                                    }
                                }
                                
                                continue;
                            }
                            
                            if(JSeaBattleRouter.readBuffer.hasArray()) {
                                byte[] _byte_msg = new byte[JSeaBattleRouter.readBuffer.limit()];
                                JSeaBattleRouter.readBuffer.get(_byte_msg);
                                String _msg = 
                                        new String(
                                                _byte_msg, 
                                                StandardCharsets.UTF_8);
                                
                                if(JSeaBattleRouter.logging) {
                                    System.out.println(
                                            String.format(
                                                    "READ client %1$s:%2$s;\n[message]\n%3$s\n", 
                                                    _sel_sc.socket().getInetAddress().getHostAddress(), 
                                                    _sel_sc.socket().getLocalPort(), _msg));
                                }
                                
                                int _connection_id = 0;
                                
                                try {
                                    _connection_id = Integer.parseInt(_msg.substring(
                                                    _msg.indexOf(
                                                            JSeaBattleRouter.idFindPattern) + 
                                                    JSeaBattleRouter.idFindPattern.length()));
                                }
                                catch (Exception e) {
                                    if(JSeaBattleRouter.logging) {
                                        System.err.println(
                                                String.format(
                                                        "Exception parse client id: %1$s\n", 
                                                        e.getMessage()));
                                    }
                                    
                                    if(JSeaBattleRouter.logging) {
                                        System.out.println(
                                                String.format(
                                                        "close channel %1$s:%2$s;\n", 
                                                        _sel_sc.socket().getInetAddress().getHostAddress(), 
                                                        _sel_sc.socket().getLocalPort()));
                                    }
                                    
                                    _sel_key.cancel();
                                    _sel_sc.socket().close();
                                }
                                
                                if(JSeaBattleRouter.connections.containsKey(_connection_id)) {
                                    BattleConnection _bc = 
                                            JSeaBattleRouter.connections.get(
                                                    _connection_id);
                                    
                                    if(_bc.isDuplex()) {
                                        _bc.redirectMessage(_sel_sc, _msg);
                                    }
                                    else {
                                        _bc.addClient2(_sel_sc, _msg);
                                    }
                                }
                                else {
                                    JSeaBattleRouter.connections.put(
                                            _connection_id, 
                                            new BattleConnection(_sel_sc, _msg));
                                }
                            }
                        }
                        catch(Exception e) {
                            if(JSeaBattleRouter.logging) {
                                System.err.println(
                                        String.format(
                                                "Exception read: %1$s\n", 
                                                e.getMessage()));
                            }
                        }
                    }
                }
                
                _keys.clear();
            }
        }
        catch(Exception e) {
            if(JSeaBattleRouter.logging) {
                System.err.println(
                        String.format(
                                "Exception select: %1$s\n", 
                                e.getMessage()));
            }
        }
    }
    
}
