package org.sibyl.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Service;

/**
 * Rootless worker processes are completely separate JVMs in a mandatory
 * bubblewrap Linux user+mount+PID+network namespace. Fail closed without bwrap.
 * No Core DB passwords, session cookies or environment are passed to workers.
 */
@Service
public final class AddonWorkerRuntime {
    private static final int MAX_BODY=65536, MAX_RESPONSE=262144;
    private final ObjectMapper mapper;
    private final Map<String,Handle> active=new HashMap<>();
    private final ExecutorService requests=Executors.newCachedThreadPool(r->{
        Thread t=new Thread(r,"sibyl-addon-ipc");t.setDaemon(true);return t;
    });
    private record Handle(Process process,Path dir,Path socket,String digest) {}
    public AddonWorkerRuntime(ObjectMapper mapper){this.mapper=mapper;}

    private static String checksum(Path archive)throws Exception{
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(archive)){byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)digest.update(buf,0,n);}
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private static void cleanup(Path dir){
        if(dir==null)return;
        try(var stream=Files.walk(dir)){
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                try{Files.deleteIfExists(p);}catch(Exception ignored){}
            });
        }catch(Exception ignored){}
    }
    private static void stop(Handle handle){
        handle.process().destroyForcibly();
        try{handle.process().waitFor(2,TimeUnit.SECONDS);}catch(InterruptedException ex){
            Thread.currentThread().interrupt();}
        cleanup(handle.dir());
    }

    private synchronized Handle ensureStarted(String id,Path archive,String jarMember)throws Exception{
        String sha=checksum(archive);
        Handle existing=active.get(id);
        if(existing!=null && existing.process().isAlive() && existing.digest().equals(sha))
            return existing;
        if(existing!=null){stop(existing);active.remove(id);}
        if(!Files.isExecutable(Path.of("/usr/bin/bwrap")))
            throw new IllegalStateException("bubblewrap required; refuse unsandboxed worker");
        if(!jarMember.matches("backend/[a-zA-Z0-9_-]{1,80}\\.jar"))
            throw new IllegalArgumentException("Invalid worker path");
        Path dir=Files.createTempDirectory(
            Path.of(System.getenv().getOrDefault("SIBYL_ADDON_DATA","/var/lib/sibyl/addons"))
                .toAbsolutePath().normalize().getParent(),".sibyl-worker-");
        Path jar=dir.resolve("worker.jar"),socket=dir.resolve("worker.sock");
        try {
            try(ZipFile zip=new ZipFile(archive.toFile())){
                ZipEntry entry=zip.getEntry(jarMember);
                if(entry==null || entry.getSize()<0 || entry.getSize()>16L*1024*1024)
                    throw new IllegalArgumentException("Invalid worker JAR entry");
                try(var in=zip.getInputStream(entry)){
                    Files.copy(in,jar);
                }
            }
            var args=new ArrayList<String>();
            java.util.Collections.addAll(args,"/usr/bin/bwrap",
                "--unshare-all","--die-with-parent","--new-session","--cap-drop","ALL",
                "--ro-bind","/usr","/usr",
                "--ro-bind","/lib","/lib");
            if(Files.exists(Path.of("/lib64")))
                java.util.Collections.addAll(args,"--ro-bind","/lib64","/lib64");
            java.util.Collections.addAll(args,
                "--ro-bind","/etc/alternatives","/etc/alternatives",
                "--ro-bind",jar.toString(),"/addon.jar",
                "--bind",dir.toString(),"/run/sibyl-worker",
                "--proc","/proc","--dev","/dev","--tmpfs","/tmp",
                "--clearenv","--setenv","HOME","/tmp","--setenv","PATH","/usr/bin",
                "/usr/bin/java","-Xmx128m","-XX:ActiveProcessorCount=1",
                "-Djava.net.preferIPv4Stack=true",
                "-jar","/addon.jar","/run/sibyl-worker/worker.sock");
            ProcessBuilder builder=new ProcessBuilder(args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            Process child=builder.start();
            Handle handle=new Handle(child,dir,socket,sha);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline){
                if(Files.exists(socket))break;
                if(!child.isAlive())throw new IllegalStateException("Worker sandbox refused to start");
                Thread.sleep(50);
            }
            if(!Files.exists(socket)) {
                stop(handle);
                throw new IllegalStateException("Worker did not create IPC socket");
            }
            active.put(id,handle);
            return handle;
        }catch(Exception ex){cleanup(dir);throw ex;}
    }

    public JsonNode call(String id,Path archive,String jarMember,String method,JsonNode body)
            throws Exception {
        if(!AddonDeploymentPlan.validId(id)
            || method==null || !method.matches("[a-z][a-zA-Z0-9_-]{0,63}"))
            throw new IllegalArgumentException("Invalid worker method");
        byte[] request=mapper.writeValueAsBytes(Map.of("method",method,"payload",
            body==null?mapper.createObjectNode():body));
        if(request.length>MAX_BODY)throw new IllegalArgumentException("Worker request too large");
        Handle handle=ensureStarted(id,archive,jarMember);
        Future<byte[]> pending=requests.submit(()->{
            try(SocketChannel channel=SocketChannel.open(StandardProtocolFamily.UNIX)){
                channel.connect(UnixDomainSocketAddress.of(handle.socket()));
                DataOutputStream out=new DataOutputStream(Channels.newOutputStream(channel));
                DataInputStream in=new DataInputStream(Channels.newInputStream(channel));
                out.writeInt(request.length);out.write(request);out.flush();
                int size=in.readInt();
                if(size<1||size>MAX_RESPONSE)throw new IllegalStateException("Bad worker response length");
                return in.readNBytes(size);
            }
        });
        try {
            JsonNode answer=mapper.readTree(pending.get(5,TimeUnit.SECONDS));
            if(answer==null || !answer.isObject())
                throw new IllegalStateException("Invalid worker response");
            return answer;
        }catch(TimeoutException ex){
            pending.cancel(true);
            deactivate(id);
            throw new IllegalStateException("Worker IPC timeout; terminated");
        }catch(ExecutionException ex){
            deactivate(id);
            throw new IllegalStateException("Worker IPC failed",ex.getCause());
        }
    }
    public void health(String id,Path archive,String jarMember)throws Exception{
        JsonNode result=call(id,archive,jarMember,"ping",mapper.createObjectNode());
        if(!result.path("ok").asBoolean(false))
            throw new IllegalStateException("Worker ping did not succeed");
    }
    public synchronized void deactivate(String id) {
        Handle handle=active.remove(id);
        if(handle!=null)stop(handle);
    }
    @PreDestroy
    public synchronized void close(){
        for(Handle handle:active.values())stop(handle);
        active.clear();
        requests.shutdownNow();
    }
}
