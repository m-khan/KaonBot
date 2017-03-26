package kaonbot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import bwapi.Color;
import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class DefenseManager extends AbstractManager {

	List<Unit> raxList = new ArrayList<Unit>();
	List<Unit> newTargetList = new ArrayList<Unit>();
	List<Position> newTargetPositions = new ArrayList<Position>();
	List<Unit> targetList = new ArrayList<Unit>();
	List<Position> targetPositions = new ArrayList<Position>();
	List<Position> defensePoints = new ArrayList<Position>();
	List<Defender> rushers = new ArrayList<Defender>();
	List<Fort> forts = new ArrayList<Fort>();
	private final double RAX_WEIGHT = 0.6;
	private final double BUNKER_WEIGHT = 0.4;
	TilePosition nextRax = null;
	TilePosition raxBase;
	int frameCount = 0;
	final int FRAME_LOCK = 51;
	final int DEFENSE_RADIUS = 400;
	final double SUPPLY_CAPPED = -1.0;
	final double NO_TARGET = -0.01;
	final double YES_TARGET = 0.01;
	final double NEW_TARGET = 0.01;
	final double ENEMY_BASE = -1.0;
	final double FRIENDLY_BASE = 1.0;
	final int BUNKER_RATING = 10;
	final int BUNKER_MAX = 3;
	final int DEFENSE_POINT_RANGE = 500;
	final double FORT_PRIORITY_INCREASE = 1.5;
	private Random r = new Random();
	private int targetListUpdateFrame = 0;
	private int targetIndex;
	private int emergencyDefenderCount = 0;
	final int EMERGENCY_DEFENDER_MAX_SUPPLY = 50;
	private List<Unit> newExpansions = new ArrayList<Unit>();
	
	public DefenseManager(double baselinePriority, double volitilityScore) {
		super(baselinePriority, volitilityScore);
		
		debugColor = new Color(100, 100, 255);
		
		raxBase = KaonBot.getStartPosition().getTilePosition();
	}
	
	@Override
	public String getName(){
		return "DEFENSE " + targetList.size() + "/" + rushers.size() + ":" + emergencyDefenderCount + "|" + forts.size() + "/" + getAllBunkers().size();
	}
	
	@Override
	public void init(Game game) {
		// TODO Auto-generated method stub
	}

	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		// TODO detection!
		if(enemy && !unit.isCloaked()){
			List<BaseLocation> bases = KaonBot.econManager.getBases();
			
			for(BaseLocation b: bases){
				if(BWTA.getRegion(unit.getPosition()) == b.getRegion()){
					targetList.add(unit);
					targetPositions.add(unit.getPosition());
					KaonBot.print(unit.getType() + " added to target list");
					incrementPriority(getVolitility() * ENEMY_BASE, false);
				}
			}
		}
		else if(friendly){
			if(unit.getType().isResourceDepot() && !unit.isCompleted()){
				newExpansions.add(unit);
				updateDefensePoints();
				incrementPriority(getVolitility() * FRIENDLY_BASE, false);
			}
		}
	}
	private void updateDefensePoints(){
		defensePoints.clear();
		
		if(newExpansions.size() > 0){
			for(Unit u: newExpansions){
				defensePoints.add(u.getPosition());
			}
			return;
		} else {
	
			List<BaseLocation> bases = KaonBot.econManager.getBases();
			for(BaseLocation b: bases){
				defensePoints.add(b.getPosition());
			}
	
			if(defensePoints.size() == 0){
				defensePoints.add(KaonBot.mainPosition.getPosition());
			}
	
			if(KaonBot.getSupply() > 40){
				Set<Chokepoint> chokes = new HashSet<Chokepoint>();		
				List<Chokepoint> duplicates = new ArrayList<Chokepoint>();
				Set<BaseLocation> toAdd = new HashSet<BaseLocation>();
				
				for(BaseLocation b: bases){
					List<Chokepoint> newChokes = b.getRegion().getChokepoints();
					for(Chokepoint choke : newChokes){
						if(choke.getCenter().getDistance(b.getPosition()) > DEFENSE_POINT_RANGE){
							toAdd.add(b);
						} else if(!chokes.add(choke)){
							duplicates.add(choke);
						}
					}
				}
				for(Chokepoint choke : duplicates){
					chokes.remove(choke);
				}
				for(Chokepoint choke : chokes){
					defensePoints.add(choke.getCenter());
				}
				for(BaseLocation b: toAdd){
					defensePoints.add(b.getPosition());
				}
			}
		}
		//TODO fix bunkers
//		for(Position p: defensePoints){
//			if(getFortNear(p) == null){
//				for(Fort f: forts){
//					f.priority = f.priority / 2;
//				}
//				forts.add(new Fort(p));
//			}
//		}
	}

	public Fort getFortNear(Position p){
		for(Fort f: forts){
			if(p.getApproxDistance(f.fortCenter) < DEFENSE_POINT_RANGE){
				return f;
			}
		}
		return null;
	}
	
	class Fort {
		private final Position fortCenter;
		private TilePosition nextBunker;
		private List<Unit> bunkers = new ArrayList<Unit>();
		private List<Garrison> marineList = new ArrayList<Garrison>();
		double priority = 1.0;
		
		public Fort(Position position){
			this.fortCenter = position;
			findAndReserveBunkerPosition();
		}
		
		public void update(){
			if(nextBunker == null){
				findAndReserveBunkerPosition();
			}
			
			for (Iterator<Garrison> it = marineList.iterator(); it
					.hasNext();) {
				Garrison g = it.next();
				if(g.update()){
					it.remove();
					Claim toFree = claimList.get(g.getUnit().getID());
					if(toFree != null) toFree.free();
				}
			}
		}
		
		private void findAndReserveBunkerPosition(){
			Unit builder = BuildingPlacer.getInstance().getSuitableBuilder(fortCenter.toTilePosition(), usePriority(), KaonBot.defenseManager);
			if(builder != null){
				nextBunker = BuildingPlacer.getInstance().getBuildTile(builder, UnitType.Terran_Bunker, fortCenter.toTilePosition());
				if(nextBunker != null){
					BuildingPlacer.getInstance().reserve(nextBunker, UnitType.Terran_Bunker, new Color(255, 255, 255));
				}
			}
		}
		
		public boolean addBunker(Unit u){
			if(u.getTilePosition().equals(nextBunker)){
				bunkers.add(u);
				findAndReserveBunkerPosition();
				return true;
			}
			return false;
		}
		
		public int requiredMarines(){
			return bunkers.size() * 4 - marineList.size();
		}
		
		public void addMarine(Claim c){
			for(Unit u: bunkers){
				if(u.getLoadedUnits().size() < 4){
					marineList.add(new Garrison(c, u));
				}
			}
		}
		
		public ProductionOrder getBunkerOrder(){
			if(nextBunker == null || bunkers.size() > BUNKER_MAX || raxList.size() == 0) return ProductionOrder.getNullOrder();
			
			BuildingOrder toReturn = new BuildingOrder(100, 0, usePriority() * priority * BUNKER_WEIGHT / (bunkers.size() + 1), 
													   UnitType.Terran_Bunker, nextBunker, true);
			for(BuildingOrder o : ProductionQueue.getActiveOrders()){
				if(o.getSignature().equals(toReturn)){
					return ProductionOrder.getNullOrder();
				}
			}
			return toReturn;
		}
		
		class Garrison extends Behavior{
			
			final int FRAME_LOCK = 10;
			int frameCount = 0;
			Unit bunker;
			
			public Garrison(Claim unit, Unit bunker) {
				super(unit);
				this.bunker = bunker;
				unit.unit.rightClick(bunker, false);
			}

			@Override
			public boolean update() {
				if(frameCount < FRAME_LOCK){
					frameCount++;
					return false;
				}
				
				if(!claimList.containsKey(getUnit().getID())){
					return true;
				}
				
				if(!getUnit().exists() || bunker.getLoadedUnits().size() == 4){
					return true;
				}
				
				if(getUnit().isLoaded()){
					touchClaim();
				} else {
					getUnit().rightClick(bunker);
				}
				return false;
			}
			
		}
	}
	
	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		if(friendly && unit.getType() == UnitType.Terran_Barracks){
			raxList.add(unit);
		} else if(friendly && unit.getType() == UnitType.Terran_Bunker){
			for(Fort fort: forts){
				if(fort.addBunker(unit)) return;
			}
		} else if(friendly && unit.getType().isResourceDepot()){
			newExpansions.remove(unit);
			incrementPriority(getVolitility(), false);
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		double price = u.getType().mineralPrice() + u.getType().gasPrice();
		
		if(enemy){
			incrementPriority(getVolitility() * price / -100, false);
		} else if(friendly){
			if(!claimList.containsKey(u.getID()) || u.getType().isWorker()){
				incrementPriority(getVolitility() * price / 100, false);
			}
			if(u.getType().isBuilding()){
				Fort f = getFortNear(u.getPosition());
				if(f != null){
					f.priority *= FORT_PRIORITY_INCREASE;
				}
				if(u.getType().isResourceDepot()){
					newExpansions.remove(u);
				}
			}
			if(u.getType() == UnitType.Terran_Barracks){
				raxList.remove(u);
			}else if(u.getType() == UnitType.Terran_Bunker){
				//TODO remove bunkers
			}
		}
	}
	
	public List<Unit> getAllBunkers(){
		List<Unit> toReturn = new LinkedList<Unit>();
		for(Fort f: forts){
			toReturn.addAll(f.bunkers);
		}
		return toReturn;
	}
	
	public void unloadAll(){
		for(Unit b: getAllBunkers()){
			b.unloadAll();
		}
		for(Fort f: forts){
			f.marineList.clear();
		}
	}
	
	public boolean needEmergencyDefenders(int extraClaims){
		return KaonBot.getSupply() < EMERGENCY_DEFENDER_MAX_SUPPLY && 
				targetList.size() > getAllBunkers().size() * BUNKER_RATING + claimList.size() + extraClaims - emergencyDefenderCount / 2;
	}
	
	public boolean needEmergencyDefenders(){
		return needEmergencyDefenders(0);
	}
	
	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		ArrayList<Double> toReturn = new ArrayList<Double>(unitList.size());
		
		int workerClaims = emergencyDefenderCount;
		
		for(Unit unit: unitList){
			UnitType type = unit.getType();
			if(!type.isWorker() && !type.isBuilding()) {
				toReturn.add(usePriority());
			}else if(type.isWorker() && needEmergencyDefenders(workerClaims)) {
				workerClaims++;
				toReturn.add(usePriority() * 10);
			}else {
				toReturn.add(DO_NOT_WANT);
			}
		}
		return toReturn;
	}

	@Override
	public void runFrame() {
		for (Iterator<Defender> iterator = rushers.iterator(); iterator.hasNext();) {
			Defender r = iterator.next();
			if(r.update()){
				iterator.remove();
				Claim toFree = this.claimList.get(r.getUnit().getID());
				if(toFree != null) toFree.free();
			}
		}
		
		for(Fort f: forts){
			f.update();
		}
		
		if(frameCount < FRAME_LOCK){
			frameCount++;
			return;
		}
		
		if(KaonBot.getSupply() > 380){
			incrementPriority(getVolitility() * SUPPLY_CAPPED, false);
		}

		if(targetList.size() == 0){
			incrementPriority(getVolitility() * NO_TARGET * rushers.size(), false);
		}
		
		//TODO: actually prioritize targets
		targetIndex = r.nextInt(100000);
		
		updateTargetList();
		updateNextRax();
		frameCount = 0;
	}

	private void updateNextRax(){
		Unit builder = BuildingPlacer.getInstance().getSuitableBuilder(KaonBot.getStartPosition().getTilePosition(), 
				getRaxPriority(), this);
		if(builder != null){
			nextRax = BuildingPlacer.getInstance().getBuildTile(builder, UnitType.Terran_Barracks, KaonBot.mainPosition.getTilePosition());
		}

	}
	
	private double getRaxPriority(){
		int raxCount = raxList.size() + 1;
		
		for(BuildingOrder b: ProductionQueue.getActiveOrders()){
			if(b.getUnitType() == UnitType.Terran_Barracks){
				raxCount++;
			}
		}
		
		return (this.usePriority() * RAX_WEIGHT) / (raxCount);
	}
	
	@Override
	public List<ProductionOrder> getProductionRequests() {
		List<ProductionOrder> prodList = new LinkedList<ProductionOrder>();
		
		for(Unit rax: raxList){
			if(rax.getAddon() != null){
				prodList.add(new UnitOrder(50, 50, this.usePriority(), rax, UnitType.Terran_Medic));
			}
			else{
				prodList.add(new UnitOrder(50, 0, this.usePriority(), rax, UnitType.Terran_Marine));
			}
		}
		
		for(Fort f: forts){
			prodList.add(f.getBunkerOrder());
		}
		
		// return now if we don't have a barracks location
		if(nextRax == null){
			return prodList;
		}

		double raxPriority = getRaxPriority();
		prodList.add(new BuildingOrder(150, 0, raxPriority, UnitType.Terran_Barracks, nextRax));
		
		return prodList;
	}

	public void updateTargetList(){
		// only do this once per frame
		if(KaonBot.getGame().getFrameCount() == targetListUpdateFrame){
			return;
		}
		targetListUpdateFrame = KaonBot.getGame().getFrameCount();

		
		targetList.clear();
		targetPositions.clear();
		for(BaseLocation b: KaonBot.econManager.getBases()){
			for(Unit e : KaonBot.getGame().getUnitsInRadius(b.getPosition(), DEFENSE_RADIUS)){
				if(KaonBot.isEnemy(e) && !e.isCloaked()){
					//incrementPriority(getVolitility() * NEW_TARGET, false);
					targetList.add(0, e);
					targetPositions.add(e.getPosition());
//					newTargetList.add(e);
//					newTargetPositions.add(e.getPosition());
				}
			}
		}

		for(Position p: defensePoints){
			for(Unit e : KaonBot.getGame().getUnitsInRadius(p, DEFENSE_RADIUS)){
				if(KaonBot.isEnemy(e) && !e.isCloaked()){
					//incrementPriority(getVolitility() * NEW_TARGET, false);
					targetList.add(0, e);
					targetPositions.add(e.getPosition());
//					newTargetList.add(e);
//					newTargetPositions.add(e.getPosition());
				}
			}
		}
		
//		// only check 1 unit each frame to cut down on performance hit
//		int index = KaonBot.getGame().getFrameCount() % KaonBot.getAllUnits().size();
//		if(index == 0)
//		{
//			if(newTargetList.size() == 0){
//				incrementPriority(getVolitility() * NO_TARGET * claimList.size(), false);
//			}
//			
//			targetList.clear();
//			targetPositions.clear();
//			targetList.addAll(newTargetList);
//			targetPositions.addAll(newTargetPositions);
//			newTargetList.clear();
//			newTargetPositions.clear();
//		}
//		Unit u = KaonBot.getAllUnits().get(index);
//		if(KaonBot.isFriendly(u) && u.getType().isBuilding()){
//			KaonBot.getGame().drawCircleMap(u.getPosition(), DEFENSE_RADIUS, new Color(0, 0, 0));
//			for(Unit e : u.getUnitsInRadius(DEFENSE_RADIUS)){
//				if(KaonBot.isEnemy(e) && !e.isCloaked()){
//					incrementPriority(getVolitility() * NEW_TARGET, false);
//					newTargetList.add(e);
//					newTargetPositions.add(e.getPosition());
//				}
//			}
//		}
	}
		
	@Override
	public void assignNewUnitBehaviors() {
		updateDefensePoints();
		
		for(Claim c: newUnits){
			if(c.unit.getType().isWorker()){
				emergencyDefenderCount++;
			}
			
			if(c.unit.getType() == UnitType.Terran_Marine)
			{
				//check bunkers first
				for(Fort f: forts){
					if(f != null && f.requiredMarines() > 0){
						f.addMarine(c);
						continue;
					}
				}
			}
			
			if(targetList.size() == 0){
				Position p = defensePoints.get(targetIndex % defensePoints.size());
				rushers.add(new Defender(c, null, p));
			}
			else{
				int target = targetIndex % targetList.size();
				rushers.add(new Defender(c, targetList.get(target), targetPositions.get(target)));
			}
		}
		newUnits.clear();
	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
		if(claim.unit.getType().isWorker()){
			emergencyDefenderCount--;
		}

		for (Iterator<Defender> iterator = rushers.iterator(); iterator.hasNext();) {
			Defender r = iterator.next();
			if(r.getUnit() == claim.unit){
				iterator.remove();
			}
		}

		for(Fort f: forts){
			for (Iterator<Fort.Garrison> iterator = f.marineList.iterator(); iterator.hasNext();) {
				Fort.Garrison r = iterator.next();
				if(r.getUnit() == claim.unit){
					iterator.remove();
				}
			}
		}
// TODO cleanup bunker garrisons
	}
	
	@Override
	public void displayDebugGraphics(Game game){
		super.displayDebugGraphics(game);
		if(nextRax != null){
			game.drawCircleMap(nextRax.toPosition(), frameCount, debugColor);
		}
		
		for(Position p: defensePoints){
			KaonBot.getGame().drawCircleMap(p, 150, new Color(0, 0, 255), false);
		}
		
		for(Defender r: rushers){
			//String toDraw = r.getUnit().getOrder().toString();
			//game.drawTextMap(r.getUnit().getPosition(), toDraw);
			KaonBot.getGame().drawLineMap(r.getUnit().getPosition(), r.targetPosition, debugColor);
			game.drawCircleMap(r.getUnit().getPosition(), r.getUnit().getGroundWeaponCooldown(), new Color(0, 0, 0));
			if(r.getUnit().isStuck()) game.drawCircleMap(r.getUnit().getPosition(), 2, new Color(255, 0, 0), true);
		}
		
		for(Fort f: forts){
			game.drawTextMap(f.fortCenter, "Bunkers: " + f.bunkers.size() + "\nMarines: " + f.marineList.size() + "\nRequired: " + f.requiredMarines());
			for(Unit b: f.bunkers){
				game.drawLineMap(f.fortCenter, b.getPosition(), new Color(0, 255, 0));
				game.drawTextMap(b.getPosition(), f.fortCenter.hashCode() + "");
			}
		}
	}

	class Defender extends Behavior{
		
		Unit target;
		Position targetPosition;
		private final int MICRO_LOCK = 12;
		private int microCount;
		
		public Defender(Claim c, Unit target, Position targetPosition) {
			super(c);
			this.target = target;
			this.targetPosition = targetPosition;
			microCount = 0;
		}

		@Override
		public boolean update() {

			if(microCount < MICRO_LOCK){
				microCount++;
				return false;
			}
			
			if(needEmergencyDefenders()){
				//make sure we hold onto emergency claims until they're not needed
				touchClaim();
			}
			
			if(!claimList.containsKey(getUnit().getID())){
				getUnit().stop();
				return true;
			}

			if(getUnit().isStuck()){
				return true;
			}
			
			if(!getUnit().exists())
			{
				KaonBot.print(getUnit().getID() + " released, does not exist.");
				return true;
			}
			if(target != null && target.exists()){
				targetPosition = target.getPosition();
//				if(getType().groundWeapon().maxRange() < getUnit().getDistance(targetPosition)){
//					KaonBot.print("IN RANGE: " + microCount);
//					getUnit().attack(target);
//					microCount = 0;
//					return false;
//				}
			}else if((getUnit().getDistance(targetPosition) < getType().sightRange()))
			{
				//KaonBot.print(getUnit().getID() + " NOTHING HERE: " + microCount);
				if(getUnit().getOrder() == Order.AttackMove){
					getUnit().stop();
				}
				
				if(target == null && targetList.size() == 0){
					//touchClaim();
					microCount = 0;
					return false;
				}
				return true;
			}
			
			
			if(getUnit().getOrder() == Order.AttackUnit ||
					getUnit().getOrder() == Order.AttackTile ||
					getUnit().getOrder() == Order.AtkMoveEP){
				//KaonBot.print("ALREADY ATTACKING: " + microCount);

				touchClaim();
				microCount = 0;
				return false;
			}
			
			// TODO: better micro
			getUnit().attack(targetPosition);
			microCount = 0;
			return false;
		}
	}


}
