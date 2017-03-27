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
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Polygon;


public class RushManager extends AbstractManager {

	List<Unit> raxList = new ArrayList<Unit>();
	Unit academy = null;
	ArrayList<Unit> targetList = new ArrayList<Unit>(100);
	ArrayList<Position> targetPositions = new ArrayList<Position>(100);
	List<Rusher> rushers = new ArrayList<Rusher>();
	private final double RAX_WEIGHT = 0.6;
	private final int MARINE_PER_MEDIC = 5;
	private int medicCounter = 0;
	private int medicTotal = 0;
	private final int MEDIC_CAP = 20;
	private final double NEW_ARMY_UNIT = 0.1;
	final double BUILDING_KILL_MULTIPLIER = 5.0;
	final double NEW_TARGET_PRIORITY = .5;
	final double SUPPLY_CAPPED = 2.0;
	TilePosition nextRax = null;
	TilePosition raxBase;
	private Position lastRusherDeath = null;
	private Position regroupPoint = null;
	private Position healSpot = null;
	private int healSpotTick = 0;
	private final int HEAL_SPOT_LIFETIME = 100;
	private boolean waitingForRushers = false;
	private int waitForNRushers = 1;
	private int NRushersSoftCap = 80;
	private int rushWaitTimeout = 1000;
	private int rushWaitCounter = 0;
	private int deadRushers = 0;
	private int targetIndex = 0;
	private ArrayList<Integer> optimalTargetIndexes = new ArrayList<Integer>(100);
	private Set<Unit> rushersWaiting = new HashSet<Unit>();
	int frameCount = 0;
	private boolean justStartLocations = true;
	private boolean canStim = false;
	
	final int FRAME_LOCK = 52;
	private Random r = new Random();
	
	public RushManager(double baselinePriority, double volitilityScore) {
		super(baselinePriority, volitilityScore);
		
		debugColor = new Color(255, 100, 100);
		
		raxBase = KaonBot.getStartPosition().getTilePosition();
	}

	@Override
	public String getName(){
		return "ATTACK " + optimalTargetIndexes.size() + "/" + targetList.size() + "|" + rushers.size() + "|" 
				+ !waitingForRushers + ":" + rushersWaiting.size() + "/" + waitForNRushers;
	}
	
	@Override
	public void init(Game game) {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy) {
		UnitType type = unit.getType();
		if(enemy){
			if(type.isBuilding()){
				targetList.add(unit);
				targetPositions.add(unit.getPosition());
				KaonBot.print(type + " added to target list");
				incrementPriority(getVolitility() * NEW_TARGET_PRIORITY, false);
			}
		}else if(friendly && !type.isWorker() && !unit.getType().isBuilding()){
			incrementPriority(getVolitility() * NEW_ARMY_UNIT, false);
			if(type == UnitType.Terran_Marine){
				medicCounter++;
			} else if(type == UnitType.Terran_Medic){
				medicCounter = 0;
				medicTotal++;
			}
			
		}
		else if(friendly && type == UnitType.Terran_Academy){
			KaonBot.econManager.setGasPriority(usePriority());
		}
	}

	public void addTarget(Unit unit, boolean addFront){
		if(unit == null || !unit.exists()){
			return;
		}
		
		if(addFront){
			targetList.add(0, unit);
			targetPositions.add(0, unit.getPosition());
		} else {
			targetList.add(unit);
			targetPositions.add(unit.getPosition());
		}
		KaonBot.print(unit.getType() + " added to target list");
		//incrementPriority(getVolitility(), false);
	}
	
	public void removeTarget(Unit unit){
		Iterator<Unit> tIt = targetList.iterator();
		Iterator<Position> pIt = targetPositions.iterator();
		
		while(tIt.hasNext() && pIt.hasNext()) {
			Unit u = tIt.next();
			pIt.next();
			
			if(u == unit){
				tIt.remove();
				pIt.remove();
				KaonBot.print(u.getType() + " removed from target list");
				//incrementPriority(-1 * getVolitility(), false);
			}
		}
		
	}
	
	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		UnitType type = unit.getType();
		if(friendly){
			if (unit.getType() == UnitType.Terran_Barracks){
				raxList.add(unit);
			} else if(type == UnitType.Terran_Academy){
				academy = unit;
				KaonBot.econManager.setGasPriority(usePriority());
			}
		}
	}

	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		UnitType type = u.getType();
		double price = type.mineralPrice() + type.gasPrice();
		
		if(enemy){
			if(type.isBuilding()){
				price = price * BUILDING_KILL_MULTIPLIER;
				//waitForNRushers = waitForNRushers / 2;
				deadRushers = 0;
			}
			incrementPriority(getVolitility() * price / 100, false);
		} else if(friendly){
			incrementPriority(getVolitility() * price / -100, false);
			
			if(claimList.containsKey(u.getID())){
				lastRusherDeath = u.getPosition();
				if(!waitingForRushers) deadRushers++;
			}
			
			if(type == UnitType.Terran_Barracks){
				raxList.remove(u);
			} else if(type == UnitType.Terran_Medic){
				medicTotal--;
			}
		}
	}
	
	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		ArrayList<Double> toReturn = new ArrayList<Double>(unitList.size());
		
		for(Unit unit: unitList){
			UnitType type = unit.getType();
			if(!type.isWorker() && !type.isBuilding()) {
				toReturn.add(usePriority());
			} else if(type.isWorker() && KaonBot.getSupply() > 380){
				toReturn.add(0.0001);
			} else {
				toReturn.add(DO_NOT_WANT);
			}
		}
		return toReturn;
	}

	@Override
	public void runFrame() {
		for (Iterator<Rusher> iterator = rushers.iterator(); iterator.hasNext();) {
			Rusher r = iterator.next();
			if(r.update()){
				iterator.remove();
				Claim toFree = this.claimList.get(r.getUnit().getID());
				if(toFree != null) toFree.free();
			}
		}

		if(frameCount < FRAME_LOCK){
			frameCount++;
			return;
		}

		if(KaonBot.hasResearched(TechType.Stim_Packs)){
			canStim = true;
		}

		if(KaonBot.getSupply() > 380){
			incrementPriority(getVolitility() * SUPPLY_CAPPED, false);
		}
		
		checkRushStrategy();
		updateNextRax();
		frameCount = 0;
	}

	private void checkRushStrategy(){
		if(targetList.size() == 0){
			waitForNRushers = 0;
		} else if(waitingForRushers && rushWaitCounter++ > rushWaitTimeout){
			waitForNRushers = (int) (waitForNRushers / 1.1);
		}
		
		if(waitingForRushers){
			if(rushersWaiting.size() > waitForNRushers){
				waitingForRushers = false;
				rushersWaiting.clear();
				for(Rusher r: rushers){
					r.forceAttack();
				}
				
			} else if(rushers.size() / 1.5 < waitForNRushers && NRushersSoftCap < waitForNRushers){
				waitForNRushers = (int) Math.floor(rushers.size() / 1.5);
			}
		} else if(deadRushers > waitForNRushers && targetList.size() > 0){
			// Attack deemed failure
			waitingForRushers = true;
			waitForNRushers += rushers.size() / 2;
			deadRushers = 0;
			targetIndex = r.nextInt(1000000);
			KaonBot.econManager.incrementPriority(KaonBot.econManager.getVolitility(), false);
		}
		
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
		
		int mCounter = medicCounter;
		if(academy != null){
			if(!academy.exists()){
				academy = null;
			} else if(!canStim && KaonBot.getGas() > 100){
				prodList.add(new ResearchOrder(100, 100, this.usePriority() + 0.001, academy, TechType.Stim_Packs));
			} else if(academy.canUpgrade(UpgradeType.U_238_Shells)){
				prodList.add(new UpgradeOrder(150, 100, this.usePriority() + 0.001, academy, UpgradeType.U_238_Shells));
			}
		}
		
		for(Unit rax: raxList){
			if(academy != null && mCounter > MARINE_PER_MEDIC && KaonBot.getGas() >= 50 && medicTotal < MEDIC_CAP){
				prodList.add(new UnitOrder(50, 50, this.usePriority(), rax, UnitType.Terran_Medic));
				mCounter = 0;
			} else if(academy != null && mCounter > MARINE_PER_MEDIC){
				KaonBot.econManager.setGasPriority(usePriority());
			} else{
				prodList.add(new UnitOrder(50, 0, this.usePriority(), rax, UnitType.Terran_Marine));
				mCounter++;
			}
		}

		// return now if we don't have a barracks location
		if(nextRax == null){
			return prodList;
		}

		if(academy == null && raxList.size() >= MARINE_PER_MEDIC / 2){
			boolean queueAcademy = true;
			for(BuildingOrder o: ProductionQueue.getActiveOrders()){
				if(o.getUnitType() == UnitType.Terran_Academy){
					queueAcademy = false;
				}
			}
			if(queueAcademy){
				prodList.add(new BuildingOrder(150, 0, this.usePriority(), UnitType.Terran_Academy, nextRax));
			}
		} else {
			double raxPriority = getRaxPriority();
			prodList.add(new BuildingOrder(150, 0, raxPriority, UnitType.Terran_Barracks, nextRax));
		}
		return prodList;
	}

	public void updateTargetList(){
		Set<Integer> enemies = KaonBot.discoveredEnemies().keySet();
		
		Iterator<Unit> tIt = targetList.iterator();
		Iterator<Position> pIt = targetPositions.iterator();
		
		while(tIt.hasNext() && pIt.hasNext()) {
			Unit u = tIt.next();
			pIt.next();
			
			if(!enemies.contains(u.getID())){
				tIt.remove();
				pIt.remove();
				KaonBot.print(u.getType() + " removed from target list");
				incrementPriority(-1 * getVolitility(), false);
			}
		}
		
		optimalTargetIndexes.clear();

		BaseLocation baseToTarget = KaonBot.scoutManager.getBestAttackLocation();
		if(baseToTarget == null){
			return;
		}
		Polygon targetPoly = baseToTarget.getRegion().getPolygon();
		int index = 0;
		tIt = targetList.iterator();
		pIt = targetPositions.iterator();

		while(tIt.hasNext() && pIt.hasNext()) {
			tIt.next();
			Position p = pIt.next();
		
			if(targetPoly.isInside(p)){
				optimalTargetIndexes.add(index);
			}

			index++;
		}
	}
	
	@Override
	public void assignNewUnitBehaviors() {
		updateTargetList();

		if(targetList.size() > 0){
			justStartLocations = false;
		}
		
		if(healSpot != null && KaonBot.getGame().getFrameCount() - HEAL_SPOT_LIFETIME > healSpotTick){
			healSpot = null;
		}
		
		for(Claim c: newUnits){
			if(targetList.size() == 0){
				
				List<BaseLocation> starts;
				if(justStartLocations){
					starts = BWTA.getStartLocations();
				} else {
					starts = BWTA.getBaseLocations();
				}
				Position p = starts.get(r.nextInt(starts.size())).getPosition();
				rushers.add(new Rusher(c, null, KaonUtils.getRandomPositionNear(p, 100)));
			}
			else{
				if(c.unit.getType() == UnitType.Terran_Medic && healSpot != null){
					rushers.add(new Rusher(c, null, healSpot));
				} else {
					Unit uToAttack = null;
					Position pToAttack = null;
					if(optimalTargetIndexes.size() > 0){
						uToAttack = targetList.get(optimalTargetIndexes.get(targetIndex % optimalTargetIndexes.size()));
						pToAttack = targetPositions.get(optimalTargetIndexes.get(targetIndex % optimalTargetIndexes.size()));
					} else {
						uToAttack = targetList.get(targetIndex % targetList.size());
						pToAttack = targetPositions.get(targetIndex % targetList.size());
					}
					
					rushers.add(new Rusher(c, uToAttack, pToAttack));
				}
			}
		}
		newUnits.clear();
	}

	@Override
	protected void addCommandeerCleanup(Claim claim) {
		for (Iterator<Rusher> iterator = rushers.iterator(); iterator.hasNext();) {
			Rusher r = iterator.next();
			if(r.getUnit() == claim.unit){
				rushersWaiting.remove(r.getUnit());
				iterator.remove();
			}
		}
	}
	
	@Override
	public void displayDebugGraphics(Game game){
		super.displayDebugGraphics(game);

		if(nextRax != null){
			game.drawCircleMap(nextRax.toPosition(), frameCount, debugColor);
		}
		
		for(Rusher r: rushers){
//			String toDraw = /*toString() + "\n" +*/ r.getUnit().getOrder().toString();
//			if(r.getUnit().getTarget() != null){
//				toDraw += "\ngetTarget(): " + r.getUnit().getTarget().getType();
//			}
//			if(r.getUnit().getOrderTarget() != null){
//				toDraw += "\ngetOrderTarget(): " + r.getUnit().getOrderTarget().getType();
//			}
//			if(r.getUnit().isStartingAttack()){
//				toDraw += "\nisStartingAttack";
//			}
//			if(r.getUnit().isAttackFrame())
//			{
//				toDraw += "\nisAttackFrame";
//			}
//			if(r.getUnit().isAttacking());
//			{
//				toDraw += "\nisAttacking";
//			}
			//game.drawTextMap(r.getUnit().getPosition(), toDraw + "\n" + r.getUnit().getGroundWeaponCooldown());
			KaonBot.getGame().drawLineMap(r.getUnit().getPosition(), r.targetPosition, debugColor);
			game.drawCircleMap(r.getUnit().getPosition(), r.getUnit().getGroundWeaponCooldown(), new Color(0, 0, 0));
			if(r.getUnit().isStuck()) game.drawCircleMap(r.getUnit().getPosition(), 2, new Color(255, 0, 0), true);
		}
	}

	class Rusher extends Behavior{
		
		Unit target;
		Position targetPosition;
		private final int MICRO_LOCK = 8;
		private int microCount;
		
		public Rusher(Claim c, Unit target, Position targetPosition) {
			super(c);
			this.target = target;
			this.targetPosition = targetPosition;
			microCount = 0;
		}

		public void forceAttack(){
			getUnit().attack(targetPosition);
			rushersWaiting.remove(getUnit());
		}
		
		@Override
		public boolean update() {
			if(microCount < MICRO_LOCK){
				microCount++;
				return false;
			}
			
			if(!claimList.containsKey(getUnit().getID())){
				getUnit().stop();
				return true;
			}
			
			if(getUnit().isStuck()){
				return true;
			}
			
			if(!getUnit().exists() || getUnit().getOrder() == null)
			{
				KaonBot.print(getUnit().getID() + " released, does not exist.");
				return true;
			}
			
			// if it's fighting we just let it do it's thing
			if(	getUnit().getOrder() == Order.AttackUnit) {
				if(getUnit().getOrderTarget() != null && getUnit().getOrderTarget().getType().isBuilding()){
					addTarget(getUnit().getTarget(), true);
				} else if(getUnit().getTarget() != null && getUnit().getTarget().getType().isBuilding()){
					addTarget(getUnit().getTarget(), true);
				}
				if(canStim && !getUnit().isStimmed()){
					getUnit().useTech(TechType.Stim_Packs);
				}
				
				healSpot = getUnit().getPosition();
				healSpotTick = KaonBot.getGame().getFrameCount();
				touchClaim();
				microCount = 0;
				return false;
			}
			
			if( getUnit().isLoaded()){
				touchClaim();
				KaonBot.defenseManager.unloadAll();
				microCount = 0;
				return false;
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
				//KaonBot.print(getUnit().getID() + " NOTHING HERE: " + target + ", " + targetPosition);
				if(target != null){
					removeTarget(target);
				}
				return true;
			}
			
			if(lastRusherDeath != null && waitingForRushers){
				if(getUnit().getDistance(lastRusherDeath) < getType().sightRange() * 3){
					if(rushersWaiting.add(getUnit())){
						regroupPoint = getUnit().getPosition();
					}
					getUnit().attack(KaonUtils.getRandomPositionNear(regroupPoint, waitForNRushers));
					touchClaim();
					microCount = 0;
					return false;
				} else {
					getUnit().attack(lastRusherDeath);
					microCount = 0;
					return false;
				}
			}
			
			if( getUnit().getOrder() == Order.AttackMove){
				if(r.nextInt(1000) == 0){ // attempt to clear blockages (slowly but shouldn't interfere too much with other stuff)
					getUnit().attack(targetPosition);
				}
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
