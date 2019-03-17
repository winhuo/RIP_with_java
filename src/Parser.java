import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;



public class Parser {

    private final static String ROUTER_ID = "router-id";
    private final static String INPUT_PORTS = "input-ports";
    private final static String OUTPUT_PORTS = "output-ports";



    public static Map<String, Object> ParseConfig(String[] args) {
        Map<String, Object> config = new HashMap<String, Object>();

        try
        {
            BufferedReader br = new BufferedReader(new FileReader(args[0]));

            String line = br.readLine();
            String[] splitFirstLine = line.split(", ");

            line = br.readLine();
            String[] splitSecondLine = line.split(", ");

            line = br.readLine();
            String[] splitThirdLine = line.split(", ");

            br.close();

            if(splitFirstLine[0].equals(ROUTER_ID))
            {
                int router_id = Integer.parseInt(splitFirstLine[1]);

                if(1 <= router_id && router_id < 64000)
                {
                    config.put(ROUTER_ID, Integer.parseInt(splitFirstLine[1]));
                }
                else
                {
                    return null;
                }
            }
            else
            {
                return null;
            }

            if(splitSecondLine[0].equals(INPUT_PORTS))
            {
                int[] input_ports = new int[splitSecondLine.length-1];

                for(int i = 1; i < splitSecondLine.length; i++)
                {
                    int input_port = Integer.parseInt(splitSecondLine[i]);

                    if(1024 <= input_port && input_port <= 64000)
                    {
                        input_ports[i-1] = Integer.parseInt(splitSecondLine[i]);
                    }
                    else
                    {
                        return null;
                    }
                }

                config.put(INPUT_PORTS, input_ports);
            }
            else
            {
                return null;
            }

            if(splitThirdLine[0].equals(OUTPUT_PORTS))
            {
                int[][] output_ports = new int[splitThirdLine.length-1][3];

                for(int i = 1; i < splitThirdLine.length; i++)
                {
                    String[] outputs = splitThirdLine[i].split("-");
                    int[] output_objects = new int[3];
                    for(int j = 0; j < outputs.length; j++)
                    {
                        output_objects[j] = Integer.parseInt(outputs[j]);
                    }

                    if(1024 <= output_objects[0] && output_objects[0] <= 64000)
                    {
                        output_ports[i-1] = output_objects;
                    }
                    else
                    {
                        return null;
                    }
                }

                config.put(OUTPUT_PORTS, output_ports);
            }
            else
            {
                return null;
            }

            return(config);

        }

        catch (FileNotFoundException fnfe)
        {
            System.out.println("file not found");
        }

        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }

        return null;

    }



    public static void PrintConfig(Map<String, Object> config)
    {
        int router_id = (int) config.get(ROUTER_ID);
        int[] inputs = (int[]) config.get(INPUT_PORTS);
        int[][] outputs = (int[][]) config.get(OUTPUT_PORTS);

        System.out.println(ROUTER_ID + ": " + router_id);

        String input_str = INPUT_PORTS + ": ";
        for(int i = 0; i < inputs.length; i++)
        {
            input_str += inputs[i] + " ";
        }

        System.out.println(input_str);

        for(int i = 0; i < outputs.length; i++)
        {
            System.out.println("output to destination router: " + outputs[i][2] + ", to port: " + outputs[i][0] + ", with metric cost: " + outputs[i][1]);
        }
    }

}


