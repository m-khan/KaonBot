package kaonbot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Polygon;


public class ScoutManager extends AbstractManager {

	private ArrayList<BaseScout> scouts = new ArrayList<BaseScout>();
	private Map<BaseLocation, Integer> lastChecked = new HashMap<BaseLocation, Integer>();
	private Map<BaseLocation, Integer> numBuildings = new HashMap<BaseLocation, Integer>();
	
	final double SPOTTED_DECREMENT = 0.1;
	final double NEED_CLAIMS = 0.005;
	final double SCOUT_RATIO; // decided based on baselinePriority
	final double SCOUT_RATIO_MULTIPLIER = 0.2; //max amount of supply that can be scouts
	final double NO_LOCATIONS_TO_CHECK = -1.0;
	final int SCOUT_POLY_DISTANCE_MULTIPLIER = 1;
	
	public ScoutManager(double baselinePriority, double volatility) {
		super(baselinePriority, volatility);
		
		SCOUT_RATIO =  baselinePriority * SCOUT_RATIO_MULTIPLIER;
		
		debugColor = new Color(100, 255, 100);
	}
	
	@Override
	public String getName(){
		return "SCOUTS " + scouts.size();
	}

	@Override
	public void init(Game game) {
		for(BaseLocation b: BWTA.getBaseLocations()){
			lastChecked.put(b, null);
			numBuildings.put(b, 0);
		}
	}

	public void setChecked(BaseLocation b){
		lastChecked.put(b, KaonBot.getGame().getFrameCount());
	}
	
	public boolean isBaseSafe(BaseLocation location){
		return numBuildings.get(location) <= 0;
	}
	
	public BaseLocation getBestAttackLocation(){
		int min = 10000;
		BaseLocation toAttack = null;
		
		for(BaseLocation b: numBuildings.keySet()){
			int num = numBuildings.get(b);
			if(num > 0 && num < min){
				toAttack = b;
				min = num;
			}
		}
		
		return toAttack;
	}
	
	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		if(enemy && unit.getType().isBuilding()){
			for(BaseLocation b: BWTA.getBaseLocations()){
				if(b.getRegion().getPolygon().isInside(unit.getPosition()))
				{
					numBuildings.put(b, numBuildings.get(b) + 1);
					setChecked(b);
					incrementPriority(getVolitility() * SPOTTED_DECREMENT, false);
				}
			}
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		if(enemy && u.getType().isBuilding()){
			for(BaseLocation b: BWTA.getBaseLocations()){
				if(b.getRegion().getPolygon().isInside(u.getPosition()))
				{
					numBuildings.put(b, numBuildings.get(b) - 1);
					setChecked(b);
					incrementPriority(getVolitility() * SPOTTED_DECREMENT, false);
				}
			}
		}
	}
	
	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		// don't need to do anything here
	}

	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		
		ArrayList<Double> toReturn = new ArrayList<Double>();
		boolean needClaims = false;
		
		
		if((KaonBot.getSupply() * SCOUT_RATIO) / 2 > scouts.size() + 1 && scouts.size() < KaonBot.econManager.getBases().size()){
			needClaims = true;
			incrementPriority(getVolitility() * NEED_CLAIMS, false);
		} else {
			//KaonBot.print(KaonBot.getSupply() / (2 * SCOUT_RATIO) + " < " + scouts.size() + 1);
		}
		
		for(int i = 0; i < unitList.size(); i++){
			if(!needClaims){
				toReturn.add(DO_NOT_WANT);
			} else {
				toReturn.add(usePriority(1.0 / (scouts.size() + 1)));
			}
		}
		
		return toReturn;
	}

	@Override
	public void runFrame() {
		List<BaseScout> toRemove = new LinkedList<BaseScout>();
		for(BaseScout scout: scouts){
			if(scout.update()){
				toRemove.add(scout);
			}
		}
		
		for(BaseScout s: toRemove){
			scouts.remove(s);
			Claim toFree = claimList.get(s.getUnit().getID());
			if(toFree != null) toFree.free();
		}

	}

	@Override
	public List<ProductionOrder> getProductionRequests() {
		// this manager doesn't build anything
		return new ArrayList<ProductionOrder>();
	}

	@Override
	public void assignNewUnitBehaviors() {
		// we only assign one unit here, we remove the rest
		
		double max = 0;
		Claim maxClaim = null;
		for(Claim c: newUnits){
			double value = c.unitType.topSpeed() + c.unitType.groundWeapon().damageAmount();
			if(max < value){
				max = value;
				maxClaim = c;
			}
		}
		
		List<Claim> toFree = new ArrayList<Claim>();
		for(Claim c: newUnits){
			if(c != maxClaim){
				toFree.add(c);
			}
		}
		for(Claim c: toFree){
			c.free();
		}
		
		if(maxClaim != null){
			//KaonBot.print("NEW CLAIM: " + maxClaim, true);
			
			BaseLocation toCheck = null;
			
			List<BaseLocation> activeScouts = getActiveScoutLocations();
			List<BaseLocation> ourBases = KaonBot.econManager.getBases();

			boolean isStart = false;
			
			// check for unscouted start locations first
			for(BaseLocation b: BWTA.getStartLocations()){
				if(activeScouts.contains(b) || ourBases.contains(b)){
					continue;
				}
				
				if(lastChecked.get(b) == null){
					toCheck = b;
					isStart = true;
					break;
				}
			}
			
			if(toCheck == null){
				Integer oldest = KaonBot.getGame().getFrameCount();
				
				for(BaseLocation b: BWTA.getBaseLocations()){
					if(activeScouts.contains(b) || ourBases.contains(b)){
						continue;
					}
					
					Integer checked = lastChecked.get(b);
					
					if(checked == null){
						toCheck = b;
						break;
					} else {
						if(oldest > checked){
							oldest = checked;
							toCheck = b;
						}
					}
				}
				// get oldest "checked" base location
			}
			
			// TODO non-start locations
			
			if(toCheck != null){
				scouts.add(new BaseScout(maxClaim, toCheck, isStart));
			} else {
				KaonBot.print(getName() + ": No scout locations to check");
				incrementPriority(getVolitility() * NO_LOCATIONS_TO_CHECK, false);
			}
		}

		newUnits.clear();
	}

	public List<BaseLocation> getActiveScoutLocations(){
		List<BaseLocation> toReturn = new ArrayList<BaseLocation>(scouts.size());
		for(BaseScout scout: scouts){
			toReturn.add(scout.toScout);
		}
		return toReturn;
	}
	
	@Override
	protected void addCommandeerCleanup(Claim claim) {
		Iterator<BaseScout> it = scouts.iterator();
		while(it.hasNext()){
			BaseScout bs = it.next();
			if(bs.getUnit() == claim.unit){
				it.remove();
			}
		}
	}

	private class BaseScout extends Behavior{

		BaseLocation toScout;
		int polygonIndex = 0;
		int fullLapIndex = -1;
		final int MICRO_LOCK = 5;
		boolean foundBase = false;
		final boolean isStartScout;
		final int POLY_DISTANCE;
		final int SINGLE_POLY_TIMEOUT = 200;
		int polyFrame = 0;
		
		
		public BaseScout(Claim unit, BaseLocation base, boolean isStartScout) {
			super(unit);
			toScout = base;
			this.isStartScout = isStartScout;
			POLY_DISTANCE = (int) getType().sightRange() * SCOUT_POLY_DISTANCE_MULTIPLIER;
		}

		@Override
		public boolean update() {
			if(KaonBot.getGame().getFrameCount() % MICRO_LOCK != 0){
				return false;
			}
			
			if(!claimList.containsKey(getUnit().getID())){
				getUnit().stop();
				return true;
			}

			if(!getUnit().exists() || getUnit().getOrder() == null){
				if(KaonBot.detectionManager.requestScan(toScout.getPosition(), true)){
					setChecked(toScout);
				}
				
				return true;
			}
			
			if(getUnit().isStuck()){
				return true;
			}
			
			Polygon poly = toScout.getRegion().getPolygon();
			List<Position> points = poly.getPoints();
			
			if(!foundBase && getUnit().getDistance(toScout.getPoint()) < getType().sightRange()){
				foundBase = true;
				Position nearestPoint = poly.getNearestPoint(getUnit().getPosition());
				
				for(int i = 0; i < points.size(); i++){
					if(nearestPoint.equals(points.get(i))){
						polygonIndex = i;
						if(polygonIndex >= poly.getPoints().size()){
							polygonIndex = 0;
						}
						break;
					}
				}
				
				fullLapIndex = polygonIndex;
				setChecked(toScout);
				if(isStartScout && numBuildings.get(toScout) <= 0){
					return true;
				}
				return false;
			} else {
				touchClaim();
				getUnit().move(toScout.getPosition());
			}
			
			if(foundBase){
				// check bounds

				if(getUnit().getPosition().getDistance(points.get(polygonIndex)) < POLY_DISTANCE || 
						KaonBot.getGame().getFrameCount() - SINGLE_POLY_TIMEOUT > polyFrame){
					polyFrame = KaonBot.getGame().getFrameCount();
					polygonIndex++;
					if(polygonIndex >= poly.getPoints().size()){
						polygonIndex = 0;
					}
				}
				
				if(polygonIndex == fullLapIndex){
					setChecked(toScout);
					if(numBuildings.get(toScout) <= 0){
						return true;
					}
				}
				touchClaim();
				
				Position moveTo = poly.getPoints().get(polygonIndex);
				moveTo = KaonUtils.translatePositionSetDistance(moveTo, toScout.getPosition(), POLY_DISTANCE / 2);
				//KaonBot.getGame().drawCircleMap(moveTo, 5, debugColor);
				getUnit().move(moveTo);
			}
			return false;
		}
	}
	
	@Override
	public void displayDebugGraphics(Game game){
		super.displayDebugGraphics(game);
		
		try {
			for(BaseScout s: scouts){
				if(s.foundBase){
					game.drawLineMap(s.getUnit().getPosition(), s.toScout.getRegion().getPolygon().getPoints().get(s.polygonIndex), debugColor);
					game.drawCircleMap(s.getUnit().getPosition(), s.POLY_DISTANCE, debugColor);
					game.drawTextMap(s.getUnit().getPosition(), s.polygonIndex + " - " + numBuildings.get(s.toScout) + " - " + lastChecked.get(s.toScout));
				} else {
					game.drawLineMap(s.getUnit().getPosition(), s.toScout.getPosition(), debugColor);
				}
			}
		} catch (Exception e) {
			// Ignore All debug output stuff
		}
		
		
		
	}

}
