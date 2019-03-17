import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;



public class Daemon {

    private int router_id;
    private int[][] output_ports;
    private Map<Integer, RoutingTableEntry> routingTable = new HashMap<Integer, RoutingTableEntry>();
    private Selector selector;
    private boolean periodicSend = true;//周期发送
    private boolean heartSend = true;//周期发送心跳包
    private Map<Integer,Timer> heartTimers=new HashMap<>();
    private Map<Integer,Timer> garbageTimers = new HashMap<Integer, Timer>();
    final private int AF_INET = 2;
    final int INFINITY =16;//无限时间
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private int print_time=0;
    private int send_time=0;
    private int heart_beat=0;
    private int garbage_time=6000;


    public Daemon(int router_id, int[] input_ports, int[][] output_ports) throws IOException{
        super();
        this.router_id = router_id;
        this.output_ports = output_ports;
        selector = Selector.open();

        readConfigFile();

        for(int i = 0; i < input_ports.length; i++){
            DatagramChannel channel = DatagramChannel.open();
            channel.socket().bind(new InetSocketAddress(input_ports[i]));
            channel.configureBlocking(false);//通道设置成非阻塞的
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            heartTimers.put(output_ports[i][2], new Timer());
        }
    }


    //读配置文件；
    //主要是心跳包时间，发送时间间隔，打印时间间隔；
    public void readConfigFile()
    throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader("G:\\MyRIP\\Configuration\\config.txt"));

        String line = br.readLine();
        String[] splitFirstLine = line.split(": ");

        line = br.readLine();
        String[] splitSecondLine = line.split(": ");

        line = br.readLine();
        String[] splitThirdLine = line.split(": ");

        br.close();

        if(splitFirstLine[0].equals("heartbeat"))
        {
            //确定心跳包时长；
            this.heart_beat=Integer.parseInt(splitFirstLine[1]);
            System.out.println("heartbeat:"+this.heart_beat);
        }

        if(splitSecondLine[0].equals("sendtime"))
        {
            //确定发送间隔
           this.send_time=Integer.parseInt(splitSecondLine[1]);
            System.out.println("sendtime:"+this.send_time);
        }

        if(splitThirdLine[0].equals("printtime"))
        {
            //确定打印间隔
            this.print_time=Integer.parseInt(splitThirdLine[1]);
            System.out.println("printtime:"+this.print_time);
        }

    }


    public void select() throws IOException {
        selector.selectNow();

    }


    public void isSelected() throws IOException {
        Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();//迭代器
        while (selectedKeys.hasNext()) {
            SelectionKey key = (SelectionKey) selectedKeys.next();

            if(key.isAcceptable()) {
                // a connection was accepted by a ServerSocketChannel.
                System.out.println("A connection was accepted");//连接被接受

            } else if (key.isConnectable()) {
                // a connection was established with a remote server.
                System.out.println("A connection was established");//连接已经建立

            } else if (key.isReadable()) {
                receivePacket((DatagramChannel) key.channel());//读

            } else if (key.isWritable() && periodicSend) {//发更新信息
                sendPackets(false);
                periodicSend = false;
            }else if(key.isWritable()&&heartSend) {//发心跳包
                sendHeartPackets();
                heartSend=false;
            }

            selectedKeys.remove();
        }
    }


    public synchronized void sendHeartPackets()throws IOException
    //与sendpackets可能需要同步控制；
    {
        buffer.clear();
        for(int i = 0; i < output_ports.length; i++) {
            createHeartBuffer();//创建心跳包
            buffer.flip();
            DatagramChannel sender = DatagramChannel.open();
            sender.send(buffer, new InetSocketAddress("localhost", output_ports[i][0]));//send方法内部实现锁
            sender.close();
            buffer.clear();
        }

    }


    public synchronized void sendPackets(boolean triggered) throws IOException
    {//同步关键字 synchronized triggered 触发的

        synchronized(System.out){
        buffer.clear();
        String text = "periodic";//固定的
        if(triggered) {
            text = "triggered";//触发的
        }
        System.out.print("Sending " + text + " packets: ");
        String end = ", ";

        for(int i = 0; i < output_ports.length; i++) {
            String begin = "";
            createBuffer();//创建路径更新信息
            buffer.flip();
            DatagramChannel sender = DatagramChannel.open();
            int bytesSent = sender.send(buffer, new InetSocketAddress("localhost", output_ports[i][0]));//send方法内部实现锁
            if(i == output_ports.length-1){
                end = ".";
            }
            else if(i == 0){
                begin = bytesSent + "Bytes to ports: ";
            }
            System.out.print(begin + output_ports[i][0] + end);
            sender.close();
            buffer.clear();
        }
        System.out.println("\n");}

    }


    private void createBuffer() {

        byte[] header = new byte[4];
        byte[] entries = new byte[36*routingTable.size()];
        header[0] = new Integer(2).byteValue();
        header[1] = new Integer(2).byteValue();
        header[3] = new Integer(router_id).byteValue();
        buffer.put(header);
        for(int i = 0; i < routingTable.size(); i++){
            entries[36*i+1] = new Integer(AF_INET).byteValue();
        }
        int i = 0;
        for(Integer key: routingTable.keySet()) {
            entries[36*i+7] = key.byteValue();
            entries[36*i+15] = new Integer(routingTable.get(key).getFirst_hop_id()).byteValue();
            entries[36*i+19] = new Integer(routingTable.get(key).getCost()).byteValue();
            for(int j=0;j<routingTable.get(key).getPath().length;++j){
                entries[36*i+20+j]=new Integer(routingTable.get(key).getPathNode(j)).byteValue();
            }
            i++;
        }
        if (buffer.remaining() > entries.length) {
            buffer.put(entries);
        }
        else
        {
            buffer.clear();
        }
    }
    private void createHeartBuffer()
            //心跳包内容，110(发送者ID)1101
    {
        byte[] heartPacket=new byte[8];
        heartPacket[0]=new Integer(1).byteValue();
        heartPacket[1]=new Integer(1).byteValue();
        heartPacket[3]=new Integer(router_id).byteValue();
        heartPacket[4]=new Integer(1).byteValue();
        heartPacket[5]=new Integer(1).byteValue();
        heartPacket[7]=new Integer(1).byteValue();
        buffer.put(heartPacket);
    }


    public void receivePacket(DatagramChannel channel) throws IOException{

        channel.receive(buffer);
        buffer.flip();

        int[] data = readReceivedPackets();
        if(consistentPacket(data)){//如果包是正常的路径更新包
            //System.out.println("Received Packet from " + data[3]);
            updateRoutingTable(data);//更新路由表
        }
        if(heartPackets(data))//如果是心跳包
        {
            //处理超时问题
            //System.out.println("Received heart Packet from " + data[3]);
            timeOutManage(data);
        }
    }



    private int[] readReceivedPackets(){//从buffer中读出数据

        int[] data = new int[buffer.limit()];
        int i = 0;

        while(buffer.hasRemaining()){
            data[i] = (int) buffer.get();
            i++;
        }

        buffer.clear();


        return data;
    }


    private boolean consistentPacket(int[] data){//检验包的内容是否是正常的路径更新包；

        if(data.length % 36 != 4){
            return false;
        }
        if(data[0] != 2 || data[1] != 2){
            System.out.println("Wrong fixed value: ");
            System.out.println("Command: " + data[0]);
            System.out.println("Version: " + data[1]);
            return false;
        }
        if(!checkAFI(data)){
            System.out.println("Wrong AFI: ");
            for(int i = 0; i < (data.length-4)/20; i++)
                System.out.println(data[20*i+5]);
            return false;
        }
        if(!checkMetricRange(data)){
            System.out.println("Wrong metric range: ");
            for(int i = 0; i < (data.length-4)/36; i++)
                System.out.println(data[36*i+19]);
            return false;
        }
        return true;
    }


    private boolean heartPackets(int []data){//检验包是否是心跳包
        if(data.length != 8){
            return false;

        }
        if(data[0]!=1||data[1]!=1)
        {
            return false;

        }
        if(data[4]!=1||data[5]!=1)
        {
            return false;

        }
        if(data[6]!=0||data[7]!=1)
        {
            return false;

        }
        return true;

    }




    private boolean checkAFI(int[] data){

        for(int i = 0; i < (data.length-4)/36; i++)
            if(data[36*i+5] != AF_INET)
                return false;
        return true;
    }





    private boolean checkMetricRange(int[] data){

        for(int i = 0; i < (data.length-4)/36; i++)
            if(data[36*i+19] < 0 || data[36*i+19]>INFINITY)
                return false;
        return true;
    }





    private void updateRoutingTable(int[] data){

        int[] line = new int[3];
        int[] path =new int[16];
        for(int i = 0; i < (data.length-4)/36; i++){
            line[0] = data[36*i+11];
            line[1] = data[36*i+19];
            line[2] = data[36*i+23];
            for(int j=0;j<16;++j)
                path[j]=data[36*i+24+j];
            updateLine(data[3], line,path);
        }


    }
    private void timeOutManage(int[] data)
    {
        int next_touter=data[3];//发送端
        TimeOut(next_touter);
    }




    private void updateLine(int id, int[] line, int[] path){

        int destination = line[0];
        int cost = line[2];
        int index_port = -1;

        for(int i = 0; i < output_ports.length; i++){
            if(output_ports[i][2] == id){
                index_port = i;
                break;
            }
        }

        int metric = Math.min(cost + output_ports[index_port][1], INFINITY);

        if(routingTable.containsKey(destination)) {//已经存在这条路径

            RoutingTableEntry route = routingTable.get(destination);



            if((route.getFirst_hop_id() == id && metric != route.getCost()) || metric < route.getCost()) {
                route.setCost(metric);
                route.setFirst_hop_id(id);
                route.setPath(id,path);
               if(metric==INFINITY)
               {
                   synchronized (routingTable)
                   {
                       routingTable.remove(destination);
                   }

               }

            }
        }
        else//不存在这条路经
        {
            if(metric != INFINITY) {
                routingTable.put(destination, new RoutingTableEntry(destination, id, metric));
                routingTable.get(destination).setPath(id,path);
            }
        }


    }



    public void setupRoutingTable() {//建立路由表
        System.out.println("Setting routing table.");
        routingTable.put(router_id, new RoutingTableEntry(router_id, router_id, 0));
        displayRoutingTable();

    }




    public void startPeriodicTimer() {//周期的
        Timer t = new Timer();
        PeriodicHandler task = new PeriodicHandler(this);//建立任务


        // [0.8*interval, 1.2*interval]
        t.scheduleAtFixedRate(task, 0, (long) (send_time));
        //被调度的任务，延迟时间，执行周期（每隔一段时间执行任务一次）
    }
    public void startHeartTimer()
    {
        Timer t=new Timer();
        HeartHandler task = new HeartHandler(this);
        t.scheduleAtFixedRate(task,0,(long)(heart_beat));
    }





    public void PRINT_router()
    {
        Timer printTimer=new Timer();
        PRINTROUT task=new PRINTROUT(this);
        printTimer.scheduleAtFixedRate(task,0,this.print_time);
    }


    public void TimeOut(int next_router)
    {
        heartTimers.get(next_router).cancel();
        heartTimers.replace(next_router,new Timer());
        heartTimeHandler task=new  heartTimeHandler(this,next_router);
        heartTimers.get(next_router).schedule(task,3*heart_beat);
    }


    class PRINTROUT extends TimerTask
    {
        private Daemon daemon;
        public PRINTROUT(Daemon daemon)
        {
            this.daemon=daemon;
        }
        @Override
        public void run() {
            daemon.displayRoutingTable();
        }

    }


    class PeriodicHandler extends TimerTask {//重复执行，置send为true

        private Daemon daemon;


        public PeriodicHandler(Daemon daemon) {
                this.daemon = daemon;
        }




        @Override
        public void run() {
            daemon.periodicSend = true;
            //System.out.println("Reseting periodicSend.");

        }
    }

    class HeartHandler extends TimerTask {//重复执行，置send为true

        private Daemon daemon;


        public HeartHandler(Daemon daemon) {
            this.daemon = daemon;
        }



        @Override
        public void run() {
            daemon.heartSend = true;


        }
    }




    class heartTimeHandler extends TimerTask {
        private Daemon daemon;
        private int route_id;

        public heartTimeHandler(Daemon daemon, int route_id) {
            this.daemon = daemon;
            this.route_id = route_id;
        }


        @Override
        public synchronized void run() {

            synchronized (routingTable) {

                for (int key : routingTable.keySet()) {
                    int[] PATH = routingTable.get(key).getPath();

                    for (int i = 0; i < PATH.length; ++i) {
                        if (PATH[i] == route_id) {


                            routingTable.get(key).setCost(INFINITY);


                        }
                    }

                }

                    routingTable.remove(route_id);

                    // sendPackets(true);


            }

        }
    }

    private void displayRoutingTable() {


        synchronized(routingTable) {//控制同步

            synchronized(System.out){
                int KEY=0;
                for(int key:routingTable.keySet())
                {
                    if(routingTable.get(key).getCost()==INFINITY-1)
                    {
                        KEY=key;
                    }
                }
                if(routingTable.containsKey(KEY))
                routingTable.remove(KEY);
            System.out.println("----- Routing Table of " + router_id + " -----");
            for(Integer key: routingTable.keySet()) {
                String space = " ";
                RoutingTableEntry entry = routingTable.get(key);
                if(entry.getCost() >= 10) {
                    space = "";
                }
                System.out.print("Dest: " + entry.getDestination_id() + ", First Hop: " + entry.getFirst_hop_id() + ", Cost: " + space + entry.getCost() +",  " );
                System.out.print("Ways:");
                for(int j=0;j<routingTable.get(key).getPath().length;++j)
                    if(routingTable.get(key).getPathNode(j)!=0)
                        System.out.print(routingTable.get(key).getPathNode(j));
                System.out.println("");
            }}
            System.out.println("");
        }
    }
}
