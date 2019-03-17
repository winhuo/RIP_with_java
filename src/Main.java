import java.util.Map;

public class Main
{
    private final static String ROUTER_ID = "router-id";
    private final static String INPUT_PORTS = "input-ports";
    private final static String OUTPUT_PORTS = "output-ports";

    public static void main(String[] args)
    {
        Map<String, Object> config=Parser.ParseConfig(args);//读配置文件；初始化节点
        if(config != null)
        {
            try
            {
                Parser.PrintConfig(config);
                Daemon daemon = new Daemon((int) config.get(ROUTER_ID), (int[]) config.get(INPUT_PORTS), (int[][]) config.get(OUTPUT_PORTS));

                daemon.setupRoutingTable();//初始时设置路由表
                //采用多线程编程
                daemon.startPeriodicTimer();//每隔一段时间把send设置成true，因为发送一次就变成false
                daemon.startHeartTimer();//功能同上，指心跳包的发送
                daemon.PRINT_router();//定时打印

                while(true) {
                    daemon.select();//
                    daemon.isSelected();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception a)
            {
                a.printStackTrace();
            }

        }
        else
        {
            System.out.println("Error parsing config file.");
        }
    }
}
