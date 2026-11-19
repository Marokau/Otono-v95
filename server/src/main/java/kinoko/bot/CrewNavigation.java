package kinoko.bot;

import kinoko.provider.map.*;
import java.util.*;

/** A small navigation graph built exclusively from native map geometry and physics.
 * Edges contain actual walks, ballistic jumps and rope climbs; there are no teleport edges.
 */
public final class CrewNavigation {
    public static final int FRAME_MS=50;
    public enum Motion { WALK, AIR, DROP, LADDER, ROPE }
    public record Point(int x,int y,int foothold,Motion motion) {}
    public record Frame(Point point,int vx,int vy,int milliseconds) {}
    public record Edge(int from,int to,List<Frame> frames) {
        public int duration() { return frames.stream().mapToInt(Frame::milliseconds).sum(); }
    }
    private final MapInfo map;
    private final PhysicsConstants physics;
    private final List<Point> points=new ArrayList<>();
    private final List<List<Edge>> edges=new ArrayList<>();
    private final Map<Integer,List<Integer>> surfaceNodes=new HashMap<>();
    private final Map<Integer,List<Integer>> ropeNodes=new HashMap<>();
    private final double walkSpeed,jumpSpeed;

    public CrewNavigation(MapInfo map,PhysicsConstants physics,int speed,int jump) {
        this.map=map;this.physics=physics;
        walkSpeed=physics.getWalkSpeed()*speed/100.0;
        jumpSpeed=physics.getJumpSpeed()*jump/100.0;
        build();
    }
    public double walkSpeed() { return walkSpeed; }
    public List<Point> points() { return List.copyOf(points); }
    public List<Edge> edgesFrom(int index) { return List.copyOf(edges.get(index)); }
    public OptionalInt groundNode(int x,int y) {
        var surface=map.getFootholdBelow(x,y-3);
        if(surface.isEmpty())return OptionalInt.empty();
        return groundNodeOnFoothold(x, surface.get().getSn());
    }

    OptionalInt groundNodeOnFoothold(int x, int foothold) {
        var ids=surfaceNodes.get(foothold);
        if(ids==null)return OptionalInt.empty();
        return ids.stream().mapToInt(Integer::intValue).boxed().min(Comparator.comparingInt(i->Math.abs(points.get(i).x-x))).map(OptionalInt::of).orElse(OptionalInt.empty());
    }
    public List<Edge> route(int from,int to) {
        double[] distance=new double[points.size()];Arrays.fill(distance,Double.POSITIVE_INFINITY);distance[from]=0;
        Edge[] previous=new Edge[points.size()];
        record Visit(int node,double cost) {}
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost));queue.add(new Visit(from,0));
        while(!queue.isEmpty()) {
            var visit=queue.remove();if(visit.cost!=distance[visit.node])continue;if(visit.node==to)break;
            for(var edge:edges.get(visit.node)) {
                double cost=visit.cost+edge.duration();
                if(cost<distance[edge.to]) {distance[edge.to]=cost;previous[edge.to]=edge;queue.add(new Visit(edge.to,cost));}
            }
        }
        if(from!=to&&previous[to]==null)return List.of();
        var result=new LinkedList<Edge>();for(int node=to;node!=from;) {var edge=previous[node];result.addFirst(edge);node=edge.from;}
        return List.copyOf(result);
    }
    private int node(Point point) {int id=points.size();points.add(point);edges.add(new ArrayList<>());return id;}
    private void edge(int from,int to,List<Frame> frames) {if(from!=to&&!frames.isEmpty())edges.get(from).add(new Edge(from,to,List.copyOf(frames)));}
    private void build() {
        for(var fh:map.getFootholds()) {
            if(fh.isWall()||fh.getX2()<=fh.getX1())continue;
            var xs=new TreeSet<Integer>();xs.add(fh.getX1());xs.add(fh.getX2());
            for(int x=fh.getX1()+24;x<fh.getX2();x+=24)xs.add(x);
            for(var rope:map.getLadderRopes())if(rope.getX()>=fh.getX1()&&rope.getX()<=fh.getX2())xs.add(rope.getX());
            var ids=new ArrayList<Integer>();for(int x:xs)ids.add(node(new Point(x,fh.getYFromX(x),fh.getSn(),Motion.WALK)));
            surfaceNodes.put(fh.getSn(),ids);
            for(int i=1;i<ids.size();i++){line(ids.get(i-1),ids.get(i),walkSpeed);line(ids.get(i),ids.get(i-1),walkSpeed);}
        }
        // Shared endpoints are physically continuous, even where WZ divides a slope into tiny pieces.
        int groundCount=points.size();
        for(int a=0;a<groundCount;a++)for(int b=0;b<groundCount;b++)if(a!=b) {
            Point p=points.get(a),q=points.get(b);
            if(p.foothold!=q.foothold&&p.x==q.x&&p.y==q.y)edge(a,b,List.of(new Frame(q,0,0,1)));
        }
        for(var rope:map.getLadderRopes()) {
            var ys=new TreeSet<Integer>();ys.add(rope.getY1());ys.add(rope.getY2());
            for(int y=rope.getY1()+24;y<rope.getY2();y+=24)ys.add(y);
            var ids=new ArrayList<Integer>();
            for(int y:ys)ids.add(node(new Point(rope.getX(),y,0,rope.getL()==1?Motion.LADDER:Motion.ROPE)));
            ropeNodes.put(rope.getSn(),ids);
            for(int i=1;i<ids.size();i++){line(ids.get(i-1),ids.get(i),walkSpeed);line(ids.get(i),ids.get(i-1),walkSpeed);}
            for(int a=0;a<groundCount;a++) {
                Point p=points.get(a);if(p.x!=rope.getX())continue;
                // Walk onto a ladder/rope only when it physically reaches the supporting surface.
                if(p.y>=rope.getY1()-6&&p.y<=rope.getY2()+12) {
                    int nearest=nearest(ids,p.x,Math.clamp(p.y,rope.getY1(),rope.getY2()));
                    line(a,nearest,walkSpeed);line(nearest,a,walkSpeed);
                }
            }
        }
        // Inputs are normal left/right/stationary jumps, including rope dismounts.
        int count=points.size();
        for(int a=0;a<count;a++)for(int direction:new int[]{-1,0,1})trajectory(a,direction,false,false);
        // A down-jump is available only on surfaces whose native WZ flags permit passing through.
        for(int a=0;a<groundCount;a++) {
            var fh=surface(points.get(a).foothold);
            if(fh.getForbidFallDown()==0 && fh.getCantThrough()==0) trajectory(a,0,true,true);
        }
        // Walking off an actual edge never drops through the supporting platform.
        for(int a=0;a<groundCount;a++)for(int direction:new int[]{-1,1}) {
            Point p=points.get(a);var fh=surface(p.foothold);
            if((direction<0&&p.x==fh.getX1())||(direction>0&&p.x==fh.getX2()))trajectory(a,direction,true,false);
        }
    }
    private Foothold surface(int id) {return map.getFootholds().stream().filter(f->f.getSn()==id).findFirst().orElseThrow();}
    private int nearest(List<Integer> ids,double x,double y) {return ids.stream().min(Comparator.comparingDouble(i->Math.abs(points.get(i).x-x)+Math.abs(points.get(i).y-y))).orElseThrow();}
    private void line(int from,int to,double speed) {
        Point a=points.get(from),b=points.get(to);double distance=Math.hypot(b.x-a.x,b.y-a.y);
        int total=Math.max(1,(int)Math.ceil(distance/speed*1000));
        var frames=new ArrayList<Frame>();
        for(int elapsed=0;elapsed<total;elapsed+=FRAME_MS) {
            int dt=Math.min(FRAME_MS,total-elapsed);double fraction=(elapsed+dt)/(double)total;
            var p=new Point((int)Math.round(a.x+(b.x-a.x)*fraction),(int)Math.round(a.y+(b.y-a.y)*fraction),b.foothold,b.motion);
            frames.add(new Frame(p,(int)Math.round((b.x-a.x)*1000.0/total),(int)Math.round((b.y-a.y)*1000.0/total),dt));
        }
        edge(from,to,frames);
    }
    private void trajectory(int from,int direction,boolean falling,boolean dropThrough) {
        Point origin=points.get(from);
        // A hanging player climbs vertically; jumping off requires a horizontal direction.
        if(origin.motion!=Motion.WALK && direction==0)return;
        double vx=direction*walkSpeed;
        double x=origin.x,y=origin.y+(dropThrough?1:0),vy=falling?0:-jumpSpeed;
        var frames=new ArrayList<Frame>();
        for(int step=0;step<100;step++) {
            double dt=FRAME_MS/1000.0;
            double nx=x+vx*dt,ny=y+vy*dt+0.5*physics.getGravityAcc()*dt*dt;
            double nextVy=Math.min(physics.getFallSpeed(),vy+physics.getGravityAcc()*dt);
            if(wallCrossed(x,y,nx,ny))return;
            // Grab a rope that the actual trajectory intersects, then climb to its nearest graph sample.
            for(var rope:map.getLadderRopes()) {
                if(origin.motion!=Motion.WALK&&step<3)continue;
                if(Math.abs(nx-rope.getX())<=5&&ny>=rope.getY1()&&ny<=rope.getY2()) {
                    int to=nearest(ropeNodes.get(rope.getSn()),rope.getX(),ny);
                    var copy=new ArrayList<>(frames);
                    var grab=new Point(rope.getX(),(int)Math.round(ny),0,rope.getL()==1?Motion.LADDER:Motion.ROPE);
                    copy.add(new Frame(grab,0,0,FRAME_MS));
                    Point end=points.get(to);int duration=Math.max(1,(int)Math.ceil(Math.abs(end.y-grab.y)/walkSpeed*1000));
                    copy.add(new Frame(end,0,Integer.signum(end.y-grab.y)*(int)walkSpeed,duration));
                    edge(from,to,copy);
                }
            }
            if(ny>y) {
                Foothold landing=null;double fraction=2;
                for(var fh:map.getFootholds()) {
                    if(fh.isWall()||fh.getX2()<=fh.getX1())continue;
                    double slope=(fh.getY2()-fh.getY1())/(double)(fh.getX2()-fh.getX1());
                    double denominator=(ny-y)-slope*(nx-x);if(denominator<=0)continue;
                    double t=(fh.getY1()+slope*(x-fh.getX1())-y)/denominator;
                    double ix=x+t*(nx-x);
                    if(!(falling&&step==0&&t<0.00001&&fh.getSn()==origin.foothold) && t>=0&&t<=1&&ix>=fh.getX1()&&ix<=fh.getX2()&&t<fraction) {landing=fh;fraction=t;}
                }
                if(landing!=null) {
                    double lx=x+fraction*(nx-x);int ly=landing.getYFromX((int)Math.round(lx));
                    int to=nearest(surfaceNodes.get(landing.getSn()),lx,ly);
                    frames.add(new Frame(new Point((int)Math.round(lx),ly,landing.getSn(),Motion.WALK),(int)vx,0,FRAME_MS));
                    Point end=points.get(to);int duration=Math.max(1,(int)Math.ceil(Math.abs(end.x-lx)/walkSpeed*1000));
                    frames.add(new Frame(end,Integer.signum(end.x-(int)lx)*(int)walkSpeed,0,duration));
                    edge(from,to,frames);return;
                }
            }
            frames.add(new Frame(new Point((int)Math.round(nx),(int)Math.round(ny),0,dropThrough&&step==0?Motion.DROP:Motion.AIR),(int)vx,(int)nextVy,FRAME_MS));
            x=nx;y=ny;vy=nextVy;
        }
    }
    private boolean wallCrossed(double x,double y,double nx,double ny) {
        for(var fh:map.getFootholds())if(fh.isWall()&&fh.isIntersect((int)Math.round(x),(int)Math.round(y),(int)Math.round(nx),(int)Math.round(ny)))return true;
        return false;
    }
}
