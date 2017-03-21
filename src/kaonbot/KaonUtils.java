package kaonbot;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import bwapi.Color;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;


public class KaonUtils {

	private static Random r = new Random();
	
	public static Position getRandomPositionNear(Position p, int range){
		if(range == 0) return p;
		int addX = r.nextInt(range * 2) - range;
		int addY = r.nextInt(range * 2) - range;
		
		return new Position(p.getX() + addX, p.getY() + addY);
	}
	
	public static Unit getClosest(Position pos, List<Unit> units){
		if(units.size() == 0) return null;
		
		Unit closest = units.get(0);
		double distance = closest.getDistance(pos);
		for(Unit u : units){
			double newDist = u.getDistance(pos);
			if(distance > newDist){
				distance = newDist;
				closest = u;
			}
		}
		return closest;
	}

	public static TilePosition getRandomBase(){
		List<Unit> allUnits = KaonBot.getAllUnits();
		List<Unit> bases = new ArrayList<Unit>();
		for(Unit u: allUnits){
			if(u.getType().isResourceDepot() && KaonBot.isFriendly(u)){
				bases.add(u);
			}
		}
		
		Random r = new Random();
		if(bases.size() == 0) return null;
		return bases.get(r.nextInt(bases.size())).getTilePosition();
	}
	
	public static Claim getClosestClaim(Position pos, List<Claim> units, UnitType ut, double priority, UnitCommander searcher){
		Iterator<Claim> it = units.iterator();

		Claim closest = null;
		double distance = Double.MAX_VALUE;
		
		while(it.hasNext()){
			Claim next = it.next();
			if(ut == null || next.unit.getType() == ut){
				double newDist = next.unit.getDistance(pos);
				if(distance > newDist && next.canCommandeer(priority, searcher)){
					distance = newDist;
					closest = next;
				}
			}
		}
		return closest;
	}
	
	public static Color getRandomColor(){
		Random rn = new Random();
		return new Color(rn.nextInt(255), rn.nextInt(255), rn.nextInt(255));

	}
}
