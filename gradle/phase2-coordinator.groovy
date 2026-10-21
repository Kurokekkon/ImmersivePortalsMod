import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

// Deliberately specific to the Phase 2 scenario, not a general game-test framework.
Path root = phaseRoot.toAbsolutePath().normalize()
def launches = new JsonSlurper().parse(root.resolve('launches.json').toFile())
def results = [status:'RUNNING', root:root.toString(), offlineMode:true, stages:[], processes:[]]
def processes = [:]
def active = new HashSet<String>()
def sequences = [server:0L, A:0L, B:0L]
long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(12)
def read = { String name ->
    def file = root.resolve(name).toFile()
    if (!file.exists()) return null
    for(int attempt=0;;attempt++) {
        try {return new JsonSlurper().parseText(Files.readString(file.toPath()))}
        catch(IOException busy) {if(attempt>=79) throw busy;Thread.sleep(5)}
    }
}
def write = { String name, Object data ->
    Path target = root.resolve(name), temp = root.resolve(name+'.coordinator.tmp')
    Files.writeString(temp,JsonOutput.prettyPrint(JsonOutput.toJson(data)))
    for(int attempt=0;;attempt++) {
        try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);break}
        catch(java.nio.file.AccessDeniedException busy) {if(attempt>=79) throw busy;Thread.sleep(5)}
    }
}
def save = { write('phase2-results.json',results) }
def await = { String label, Closure condition, long timeout = 90000L ->
    long until = Math.min(deadline,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout))
    while(System.nanoTime()<until) {
        if(condition()) return
        active.each { role -> assert processes[role].isAlive(): "${role} exited unexpectedly: ${processes[role].exitValue()}" }
        Thread.sleep(100)
    }
    throw new AssertionError("Timed out: ${label}; inspect ${root}")
}
def stage = { String name, Closure action ->
    println "[Phase 2] START ${name}"
    def item=[name:name,status:'RUNNING']; results.stages.add(item); save()
    try {
        action()
        item.status='PASS'
        write("evidence-${results.stages.size()}.json",[stage:name,server:read('server-state.json'),A:read('clientA-state.json'),B:read('clientB-state.json')])
        println "[Phase 2] PASS ${name}"
    } catch(Throwable failure) { item.status='FAIL'; item.error=failure.toString(); throw failure }
    finally {save()}
}
int port
new ServerSocket(0,1,InetAddress.getByName('127.0.0.1')).withCloseable { socket -> port=socket.localPort }
assert port!=25565
results.port=port
// EULA acceptance was explicitly authorized for disposable test servers by the user.
Files.writeString(root.resolve('server/eula.txt'),'# Disposable Phase 2 server only\neula=true\n')
Files.writeString(root.resolve('server/server.properties'),"""server-ip=127.0.0.1
server-port=${port}
online-mode=false
enforce-secure-profile=false
white-list=false
max-players=2
spawn-protection=0
view-distance=4
simulation-distance=4
gamemode=creative
level-name=world
level-type=minecraft:flat
max-tick-time=60000
enable-rcon=false
""")
def launch = { String role ->
    def config=launches[role]
    int instance=results.processes.count {it.role==role}+1
    String log="${role}-${instance}"
    def process=new ProcessBuilder(config.java.toString(),'@'+config.argfile.toString())
        .directory(new File(config.directory.toString()))
        .redirectOutput(root.resolve(log+'.stdout.log').toFile()).redirectError(root.resolve(log+'.stderr.log').toFile()).start()
    processes[role]=process; active.add(role)
    results.processes.add([role:role,pid:process.pid(),instance:instance,stdout:log+'.stdout.log',stderr:log+'.stderr.log'])
    save()
    await("${role} ready",{
        def state=read(role=='server'?'ready-server.json':"ready-client${role}.json")
        state?.pid==process.pid() && state.ready
    },120000)
}
def issue = { String role, String action, Map args=[:] ->
    long seq=++sequences[role]
    String prefix=role=='server'?'server':"client${role}"
    write(prefix+'-command.json',[seq:seq,action:action]+args)
    return [role:role,prefix:prefix,seq:seq]
}
def complete = { token ->
    await("${token.role} command ${token.seq}",{
        def response=read(token.prefix+'-response.json')
        if(response?.seq!=token.seq) return false
        assert response.status=='PASS': response.toString()
        true
    },90000)
}
def send = { String role,String action,Map args=[:] ->complete(issue(role,action,args))}
def playerName = {String role ->'PortalTest'+role}
def positions = [:]
def assertState = {Map expected ->
    await('authoritative player/client synchronization',{
        def server=read('server-state.json')
        if(server?.players?.size()!=expected.size() || server.portalCopies!=1 || !server.portalValid) return false
        for(def entry:expected.entrySet()) {
            String role=entry.key, dim=entry.value
            def p=server.players[playerName(role)], client=read("client${role}-state.json")
            if(!p || p.dimension!=dim || p.pending || p.copies!=1 || !client?.connected || client.dimension!=dim || client.uuid!=p.uuid) return false
            if(Math.abs(p.x-client.x)>0.75 || Math.abs(p.y-client.y)>0.75 || Math.abs(p.z-client.z)>0.75) return false
            if(Math.abs(Math.IEEEremainder(p.yaw.doubleValue()-180d,360d))>2 || Math.abs(p.pitch)>2) return false
            if(Math.abs(Math.IEEEremainder(client.yaw.doubleValue()-p.yaw.doubleValue(),360d))>2) return false
        }
        true
    })
    def state=read('server-state.json')
    assert state.players.values().collect{it.uuid}.unique().size()==expected.size(): 'Session UUID collision'
}
def setup = {String role,String dim ->
    send('server','setup',[player:playerName(role),dimension:dim])
    await('setup teleport acknowledged',{
        def p=read('server-state.json')?.players?.get(playerName(role))
        def c=read("client${role}-state.json")
        p && !p.pending && p.dimension==dim && c?.dimension==dim && Math.abs(p.z-c.z)<0.15
    })
}
String O='minecraft:overworld', N='minecraft:the_nether'
def peer = {String observer,String subject,String dim ->
    String uuid=read('server-state.json').players[playerName(subject)].uuid
    await("${observer} tracking ${subject} in ${dim}",{
        def matches=read("client${observer}-state.json")?.players?.findAll{it.uuid==uuid}
        matches?.size()==1 && matches[0].dimension==dim
    },15000)
}
def cross = {String role,String dim ->
    send(role,'walk',[dimension:dim])
    await('crossing acknowledged',{
        def p=read('server-state.json').players[playerName(role)]
        p?.dimension==dim && !p.pending && p.copies==1
    })
    def p=read('server-state.json').players[playerName(role)]
    assert Math.abs(p.y-(dim==N?111:81))<2 && Math.abs(p.z-(dim==N?0.5:8.5))<8: "Wrong crossing position ${p}"
}
def stopServer = {
    send('server','stop'); active.remove('server')
    def p=processes.server
    assert p.waitFor(60,TimeUnit.SECONDS): 'Server did not stop cleanly'
    assert p.exitValue()==0: 'Server exit failure'
    def stopped=read("stopped-server-${p.pid()}.json")
    assert stopped?.stopped && stopped.trackedPlayers==0 && stopped.ticketManagers==0: "Server cleanup: ${stopped}"
}
try {
    stage('standalone-server-startup'){launch('server')}
    stage('client-A-login'){launch('A');send('A','connect');assertState([( 'A'):O]); assert Math.abs(read('server-state.json').players.PortalTestA.z-0.5)<0.2}
    stage('client-B-login'){launch('B');send('B','connect');assertState([A:O,B:O]);assert Math.abs(read('server-state.json').players.PortalTestB.z-0.5)<0.2}
    stage('shared-portal-and-remote-chunks'){
        send('A','view');send('B','view')
        assert read('fixture.json').outward
        ['A','B'].each { assert read("client${it}-state.json").chunks[N]>0 }
        peer('A','B',O);peer('B','A',O)
    }
    stage('A-crosses-B-observes'){
        def before=read('server-state.json').players.PortalTestB
        cross('A',N);assertState([A:N,B:O]);peer('B','A',N)
        def after=read('server-state.json').players.PortalTestB
        assert Math.abs(before.x-after.x)<0.05 && Math.abs(before.z-after.z)<0.05: 'Observer B moved'
        send('B','ping'); send('server','ping')
    }
    stage('B-crosses-same-portal'){cross('B',N);assertState([A:N,B:N]);peer('A','B',N);peer('B','A',N)}
    stage('A-returns-B-observes'){setup('A',N);cross('A',O);assertState([A:O,B:N]);peer('B','A',O)}
    stage('B-returns'){setup('B',N);cross('B',O);assertState([A:O,B:O]);peer('A','B',O);peer('B','A',O)}
    stage('near-simultaneous-crossings'){
        setup('A',O);setup('B',O)
        def a=issue('A','walk',[dimension:N]), b=issue('B','walk',[dimension:N])
        complete(a);complete(b);assertState([A:N,B:N]);peer('A','B',N);peer('B','A',N)
    }
    stage('disconnect-A-B-remains'){
        positions.A=read('server-state.json').players.PortalTestA
        send('A','disconnect')
        await('A-specific loaders released',{
            def s=read('server-state.json')
            s.players.size()==1 && s.players.PortalTestB && !s.trackedPlayers.contains(positions.A.uuid)
        },15000)
        assert !read('clientA-state.json').initialized
        setup('B',N);cross('B',O);assertState([B:O])
    }
    stage('A-reconnects-to-running-server'){
        send('A','connect');assertState([A:N,B:O])
        def p=read('server-state.json').players.PortalTestA
        assert p.uuid==positions.A.uuid && Math.abs(p.z-positions.A.z)<0.2: 'Reconnect lost persisted player state'
        setup('A',N);cross('A',O);assertState([A:O,B:O])
    }
    stage('mounted-A-crosses-B-observes'){
        setup('A',O);setup('B',O);send('server','mount')
        await('mounted client ready',{read('clientA-state.json')?.mounted && !read('server-state.json').players.PortalTestA.pending})
        cross('A',N);assertState([A:N,B:O]);peer('B','A',N)
        def s=read('server-state.json')
        assert s.players.PortalTestA.mounted && s.vehicleCopies==1 && s.vehicleDimensions[N]==1: 'Mounted entity integrity failed'
        ['A','B'].each { role ->
            await("${role} vehicle tracking",{
                def matches=read("client${role}-state.json").vehicles.findAll{it.uuid==s.vehicleUuid}
                matches.size()==1 && matches[0].dimension==N
            },15000)
        }
        send('server','dismount');setup('A',N);cross('A',O)
    }
    stage('disconnect-both-release-loaders'){
        send('A','disconnect');send('B','disconnect')
        await('all client-owned tickets released',{
            def s=read('server-state.json');s.players.isEmpty() && s.trackedPlayers.isEmpty() && s.portalTickets==0
        },20000)
        assert !read('clientA-state.json').initialized && !read('clientB-state.json').initialized
    }
    stage('standalone-server-save-stop-restart'){
        stopServer();launch('server')
        assert read('server-state.json').portalValid && read('server-state.json').portalCopies==1
    }
    stage('both-reconnect-and-traverse-persisted-portal'){
        send('A','connect');send('B','connect');assertState([A:O,B:O])
        setup('A',O);setup('B',O);send('A','view');send('B','view')
        cross('A',N);peer('B','A',N);cross('B',N);assertState([A:N,B:N])
        peer('A','B',N);peer('B','A',N)
    }
    stage('final-client-and-server-cleanup'){
        send('A','disconnect');send('B','disconnect')
        await('final tickets released',{def s=read('server-state.json');s.players.isEmpty() && s.trackedPlayers.isEmpty() && s.portalTickets==0},20000)
        ['A','B'].each { role -> send(role,'exit');active.remove(role);assert processes[role].waitFor(30,TimeUnit.SECONDS);assert processes[role].exitValue()==0 }
        stopServer()
    }
    stage('packet-and-log-audit'){
        def suspicious=[]
        root.toFile().listFiles().findAll{it.name.endsWith('.log')}.each { file ->
            file.eachLine {line -> if(line =~ /(?i)Invalid move player packet|DecoderException|EncoderException|MixinApplyError|InjectionError|unknown dimension|unknown teleport id|duplicate entity|duplicate UUID|failed portal teleport validation|malformed redirected packet|connection reset|mixin exception|client world missing|chunk ticket leak|ServerWatchdog|portal.*(?:save|load).*exception/) suspicious.add([file:file.name,line:line])}
        }
        write('suspicious-log-lines.json',suspicious)
        assert suspicious.isEmpty(): 'Suspicious packet/runtime logs require investigation'
    }
    results.status='PASS'
} catch(Throwable failure) {
    results.status='FAIL';results.error=failure.toString()
    throw failure
} finally {
    processes.each { role,p ->
        if(p.isAlive()) {p.destroy();if(!p.waitFor(10,TimeUnit.SECONDS)) p.destroyForcibly().waitFor(10,TimeUnit.SECONDS)}
        def item=results.processes.findAll{it.pid==p.pid()}.last()
        item.exitCode=p.isAlive()?'TIMEOUT':p.exitValue()
    }
    results.processes.each { item ->
        if(item.exitCode==null) item.exitCode=0 // Earlier server instances were explicitly joined/checked above.
    }
    save()
    println "[Phase 2] ${results.status}: ${root}"
}
