package dev.gtav.trainer;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class BridgeDeadlineTest {
 static void require(boolean value){if(!value)throw new AssertionError();}
 static void deadline(boolean mutation)throws Exception{
  try(ServerSocket server=new ServerSocket(0)){
   Thread peer=new Thread(()->{
    try{
     if(mutation)for(String response:new String[]{BridgeWire.IDENTITY,"READY\tReady"}){
      try(Socket s=server.accept()){
       new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
       s.getOutputStream().write((response+"\n").getBytes());
      }
     }
     try(Socket s=server.accept()){
      new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
      for(int i=0;i<60;i++){s.getOutputStream().write('A');s.getOutputStream().flush();Thread.sleep(40);}
     }
    }catch(Exception expectedDisconnect){}
   });peer.start();
   BridgeWire wire=new BridgeWire("127.0.0.1",server.getLocalPort(),"test",500);
   long start=System.nanoTime();
   try{if(mutation)wire.execute("player.max_health","");else wire.probe();throw new AssertionError();}
   catch(BridgeWire.Failure expected){require(expected.uncertain==mutation);}
   long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
   require(elapsed>=400&&elapsed<1500);peer.join(4000);require(!peer.isAlive());
   System.out.println("PASS: total "+(mutation?"command":"probe")+" response deadline: "+elapsed+" ms");
  }
 }
 public static void main(String[] args)throws Exception{
  deadline(false);deadline(true);
  try(ServerSocket server=new ServerSocket(0)){
   BridgeWire wire=new BridgeWire("127.0.0.1",server.getLocalPort(),"test",2500);
   CountDownLatch received=new CountDownLatch(1);
   AtomicReference<Throwable> failure=new AtomicReference<>();
   Thread peer=new Thread(()->{
    try(Socket s=server.accept()){
     new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();received.countDown();
     require(s.getInputStream().read()==-1);
    }catch(Throwable error){failure.set(error);}
   });
   Thread client=new Thread(()->{
    try{wire.probe();failure.set(new AssertionError("Expected cancellation"));}
    catch(BridgeWire.Failure expected){if(expected.uncertain)failure.set(new AssertionError());}
   });peer.start();client.start();require(received.await(3,TimeUnit.SECONDS));
   wire.cancelProbe();client.join(1000);peer.join(1000);
   require(!client.isAlive()&&!peer.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());
   System.out.println("PASS: cancelling read-only probe closes socket and releases worker immediately");
  }
 }
}
