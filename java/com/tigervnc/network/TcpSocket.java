/* Copyright (C) 2002-2005 RealVNC Ltd.  All Rights Reserved.
 * Copyright (C) 2012 TigerVNC Team
 * 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package com.tigervnc.network;

import com.tigervnc.rdr.FdInStream;
import com.tigervnc.rdr.FdOutStream;
import com.tigervnc.rdr.Exception;
import com.tigervnc.rfb.LogWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.*;
import java.nio.channels.*;

public class TcpSocket extends Socket {

  // -=- Socket initialisation
  public static boolean socketsInitialised = false;
  public static void initSockets() {
    if (socketsInitialised)
      return;
    socketsInitialised = true;
  }

  // -=- TcpSocket

  public TcpSocket(SocketDescriptor sock, boolean close) {
    super(new FdInStream(sock), new FdOutStream(sock), true);
    closeFd = close;
  }

  public TcpSocket(SocketDescriptor sock) {
    this(sock, true);
  }

  public TcpSocket(String host, int port) throws Exception {
    closeFd = true;
    SocketDescriptor sock = null;
    InetAddress addr = null;
    boolean result = false;

    // - Create a socket
    initSockets();
    
    try {
      addr = java.net.InetAddress.getByName(host);
    } catch(UnknownHostException e) {
      throw new Exception("unable to resolve host by name: "+e.toString());
    }

    try {
      sock = new SocketDescriptor();
    } catch(Exception e) {
      throw new SocketException("unable to create socket: "+e.toString());
    }

    /* Attempt to connect to the remote host */
    try {
      result = sock.connect(new InetSocketAddress(addr, port));
    } catch(java.io.IOException e) {
      throw new SocketException("unable to connect:"+e.getMessage());
    }

    if (!result && sock.isConnectionPending()) {
      while (!result) {
        try {
          result = sock.finishConnect();
        } catch(java.io.IOException e) {
          throw new Exception(e.getMessage());
        }
      }
    }

    if (!result)
      throw new SocketException("unable connect to socket");

    // Disable Nagle's algorithm, to reduce latency
    enableNagles(sock, false);

    // Create the input and output streams
    instream = new FdInStream(sock);
    outstream = new FdOutStream(sock);
    ownStreams = true;
  }

  protected void finalize() throws Exception {
    if (closeFd)
      try {
        ((SocketDescriptor)getFd()).close();
      } catch (IOException e) {
        throw new Exception(e.toString());
      }
  }

  public int getMyPort() {
    SocketAddress address = ((SocketDescriptor)getFd()).socket().getLocalSocketAddress();
    return ((InetSocketAddress)address).getPort();
  }

  public String getPeerAddress() {
    SocketAddress peer = ((SocketDescriptor)getFd()).socket().getRemoteSocketAddress();
    if (peer != null)
      return peer.toString();
    return "";
  }

  public int getPeerPort() {
    SocketAddress address = ((SocketDescriptor)getFd()).socket().getRemoteSocketAddress();
    return ((InetSocketAddress)address).getPort();
  }

  public String getPeerEndpoint() {
    String address = getPeerAddress();
    int port = getPeerPort();
    return address+"::"+port;
  }

  public boolean sameMachine() {
    SocketAddress peeraddr = ((SocketDescriptor)getFd()).socket().getRemoteSocketAddress();
    SocketAddress myaddr = ((SocketDescriptor)getFd()).socket().getLocalSocketAddress();
    return myaddr.equals(peeraddr);
  }

  public void shutdown() {
    super.shutdown();
  }
  
  public void close() throws IOException {
    ((SocketDescriptor)getFd()).close();
  }
  
  public static boolean enableNagles(SocketChannel sock, boolean enable) {
    try {
      sock.socket().setTcpNoDelay(!enable);
    } catch(java.net.SocketException e) {
      vlog.error("unable to setsockopt TCP_NODELAY: "+e.getMessage());
      return false;
    }
    return true;
  }

  public static boolean isSocket(java.net.Socket sock) {
    return sock.getClass().toString().equals("com.tigervnc.net.Socket");
  }

  public boolean isConnected() {
    return ((SocketDescriptor)getFd()).isConnected();
  }

  public int getSockPort() {
    return ((InetSocketAddress)((SocketDescriptor)getFd()).socket().getRemoteSocketAddress()).getPort();
  }

  /* Tunnelling support. */
  public static int findFreeTcpPort() {
    java.net.ServerSocket sock;
    int port;
    try {
      sock = new java.net.ServerSocket(0);
      port = sock.getLocalPort();
      sock.close();
    } catch (java.io.IOException e) {
      throw new SocketException("unable to create socket: "+e.toString());
    }
    return port;
  }

  private boolean closeFd;
  static LogWriter vlog = new LogWriter("TcpSocket");

}


