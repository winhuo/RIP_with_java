


public class RoutingTableEntry {//路由表记录

    private int destination_id;
    private int first_hop_id;
    private int cost;
    private int[] Path; //the path to destination


    public RoutingTableEntry(int destinationId, int firstHopId, int metric) {
        this.setDestination_id(destinationId);
        this.setFirst_hop_id(firstHopId);
        this.setCost(metric);
        Path=new int[16];
        for(int i=0;i<Path.length;++i)
            Path[i]=0;
    }



    public int getDestination_id() {
        return destination_id;
    }



    public void setDestination_id(int destination_id) {
        this.destination_id = destination_id;
    }



    public int getFirst_hop_id() {
        return first_hop_id;
    }



    public void setFirst_hop_id(int first_hop_id) {
        this.first_hop_id = first_hop_id;
    }



    public int getCost() {
        return cost;
    }



    public void setCost(int cost) {
        this.cost = cost;
    }




    public void setPath(int newNode,int[] path){
        for(int i=0;i<Path.length;++i)
            Path[i]=0;
        Path[0]=newNode;
        int i=0;
        while(path[i]!=0&&i<path.length){
            Path[i+1]=path[i];    ++i;
        }
    }

    public int getPathNode(int index){
        return Path[index];
    }

    public int[] getPath(){
        return Path;
    }

}
