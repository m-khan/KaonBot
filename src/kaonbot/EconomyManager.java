package kaonbot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import bwapi.Color;
import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;


public class EconomyManager extends AbstractManager{
	
	private final double ENEMY_BASE = 1.0;
	private final double ENEMY_DEFENSE = 1.0;
	private final double SCV_MULT = 1.0;
	private final double SCV_SURPLUS = 0.2;
	private final int SCV_HARDCAP = 90;
	private final double EXPO_MULT = 0.5;
	//private final double GAS_MULT = 0.8;
	private final double EXPO_SATURATED = .9;
	private int NUM_BASES_TO_QUEUE = 3;
	private boolean needNewBase = false;
	private double gasPriority = 0;
	
	
	private Set<Unit> allWorkers = new HashSet<Unit>();
	private ArrayList<Base> bases = new ArrayList<Base>();
	
	public EconomyManager(double baselinePriority, double volatilityScore)
	{
		super(baselinePriority, volatilityScore);
		
		debugColor = new Color(200, 200, 200);
	}
	
	@Override
	public String getName(){
		return "ECONOMY";
	}

	public void init(Game game){
		List<BaseLocation> baseLocations = BWTA.getBaseLocations();
		BaseLocation start = BWTA.getStartLocation(game.self());
		
		for(BaseLocation l: baseLocations){
			bases.add(new Base(l, start));
		}
	}
	
	public void setGasPriority(double priority){
		if(priority > gasPriority){
			gasPriority = priority;
		}
	}
	
	public List<BaseLocation> getBases(){
		List<BaseLocation> toReturn = new LinkedList<BaseLocation>();
		for(Base b: bases){
			if(b.cc != null && b.cc.exists() && b.mins.size() > 0){
				toReturn.add(b.location);
			}
		}
		return toReturn;
	}
	
	@Override
	public void handleUnitDestroy(Unit u, boolean friendly, boolean enemy) {
		if(friendly && u.getType().isWorker()){
			allWorkers.remove(u);
			//incrementPriority(getVolitility(), false);
		}
	}

	public List<ProductionOrder> getProductionRequests(){
		PriorityQueue<ProductionOrder> list = new PriorityQueue<ProductionOrder>();
		double[] expandScores = new double[bases.size()];
		Base init = bases.get(0);
		double highScore = init.gdFromEnemy - init.gdFromStart;
		double lowScore = highScore;
		
		int totalSCVRequired = 0;
		
		// normalize and use all expand scores
		int i = 0;
		for(Base b: bases){
			totalSCVRequired += b.requiredMiners();

			double score = b.gdFromEnemy - b.gdFromStart;
			expandScores[i] = score;
			if(score > highScore){
				highScore = score;
			}
			else if(score < lowScore){
				lowScore = score;
			}
			i++;
		}
		
		for(i = 0; i < bases.size(); i++){
			Base b = bases.get(i);
			double nScore = expandScores[i];
			nScore = nScore - lowScore;
			nScore = nScore / (highScore - lowScore);
			if(b.cc == null) {
				if(totalSCVRequired > 0){
					list.add(new BuildingOrder(400, 0, this.usePriority(EXPO_MULT * nScore), 
							UnitType.Terran_Command_Center, b.location.getTilePosition()));
				} else {
					list.add(new BuildingOrder(400, 0, this.usePriority(EXPO_SATURATED * nScore), 
							UnitType.Terran_Command_Center, b.location.getTilePosition()));
				}
			} else if(b.cc.exists()){
				if(b.gas != null && b.extractor == null){
					list.add(new BuildingOrder(75, 0, gasPriority, UnitType.Terran_Refinery, b.gas.getTilePosition()));
				}
				
				if ( allWorkers.size() < SCV_HARDCAP){
					if(totalSCVRequired > 0){
						list.add(new UnitOrder(50, 0, this.usePriority(SCV_MULT), b.cc, UnitType.Terran_SCV));
						totalSCVRequired--;
					} else {
						list.add(new UnitOrder(50, 0, this.usePriority(SCV_SURPLUS), b.cc, UnitType.Terran_SCV));
					}
				}
			}
		}
		
		ArrayList<ProductionOrder> toReturn = new ArrayList<ProductionOrder>();
		int numBases = getBases().size();
		for(int q = 0; q < NUM_BASES_TO_QUEUE + numBases; q++){
			if(list.peek() != null){
				toReturn.add(list.poll());
			}
		}
		
		return toReturn;
	}
	

	public void handleNewUnit(Unit unit, boolean friendly, boolean enemy){
		UnitType type = unit.getType();
		
		if(type.isMineralField()){
			for(Base b: bases){
				double distance = b.location.getDistance(unit.getPosition());
				if(distance < 300){
					b.addMinerals(unit);
				}
			}
		}
		else if(friendly && type.isWorker()){
			allWorkers.add(unit);
		}
		else if(type == UnitType.Resource_Vespene_Geyser){
			for(Base b: bases){
				double distance = b.location.getDistance(unit.getPosition());
				if(distance < 300){
					b.gas = unit;
					gasPriority = 0;
				}
			}
		}
		else if(type.isResourceDepot()){
			if (friendly && unit.isCompleted()){
				for(Base b: bases){
					double distance = b.location.getDistance(unit.getPosition());
					if(distance < 300){
						b.cc = unit;
						KaonBot.mainPosition = b.location;
					}
				}
			} else if(enemy){
				incrementPriority(getVolitility() * ENEMY_BASE, false);
			}
		}
		else if(enemy && type.isBuilding() && type.canAttack()){
			incrementPriority(getVolitility() * ENEMY_DEFENSE, false);
		}
	}
	
	public void findNewMainBase(){
		KaonBot.mainPosition = BWTA.getNearestBaseLocation(KaonUtils.getRandomBase());
		BuildingPlacer.getInstance().clearCache();
	}

	@Override
	public void handleCompletedBuilding(Unit unit, boolean friendly) {
		if(friendly && unit.getType().isResourceDepot()){
			for(Base b: bases){
				double distance = b.location.getDistance(unit.getPosition());
				if(distance < 300){
					b.cc = unit;
					//KaonBot.mainPosition = b.location;
				}
			}
		} else if(friendly && unit.getType().isRefinery()){
			for(Base b: bases){
				double distance = b.location.getDistance(unit.getPosition());
				if(distance < 300){
					b.extractor = unit;
					//KaonBot.mainPosition = b.location;
				}
			}
		}
	}

	@Override
	public void runFrame(){
		List<Unit> toFree = new LinkedList<Unit>();
		for(Base b: bases){
			toFree.addAll(b.update());
		}
		for(Unit u: toFree){
			Claim claim = claimList.get(u.getID());
			if(claim != null){
				claim.free();
			}
		}
	}
	
	@Override
	public ArrayList<Double> claimUnits(List<Unit> unitList) {
		ArrayList<Double> claims = new ArrayList<Double>();
		
		for(Unit unit: unitList){
			if (unit.getType() == UnitType.Terran_SCV)
			{
				claims.add(this.usePriority());
				//claims.add(-1.0);
			}
			else{
				claims.add(DO_NOT_WANT);
			}
		}
		return claims;
	}

	public void removeMiner(Claim cl){
		for(Base b: bases){
			Iterator<Miner> it = b.miners.iterator();
			while(it.hasNext()){
				if(it.next().getUnit() == cl.unit){
					it.remove();
				}
			}

			it = b.gasers.iterator();
			while(it.hasNext()){
				if(it.next().getUnit() == cl.unit){
					it.remove();
				}
			}
		}
	}

	@Override
	protected void addCommandeerCleanup(Claim cl){
		cl.addOnCommandeer(cl.new CommandeerRunnable(cl) {
			@Override
			public void run() {
				removeMiner((Claim) arg);
				this.disable();
			}
		});
	}

	private Base getBaseForNewSCV(Position unitPosition){
		Base newBase = null;
		for(Base b: bases){
			double distance = b.location.getDistance(unitPosition);
			if(distance < 300 && b.requiredMiners() > 0){
				newBase = b;
				break;
			}
		}
		if(newBase == null || newBase.requiredMiners() > 0){
			// check all bases and see which needs the worker the most
			int max = -1000000;
			newBase = null;
			for(Base b: bases){
				if(b.cc != null && b.requiredMiners() > max){
					max = b.requiredMiners();
					newBase = b;
				}
			}
			if(max < 0){
				needNewBase = true;
			}
			
		}
		return newBase;
	}
	
	@Override
	public void assignNewUnitBehaviors() {
		for(Claim claim: newUnits){
			Unit unit = claim.unit;
			if(unit.exists() && unit.getType().isWorker())
			{
				// check close base to see if it needs the worker
				Base newBase = getBaseForNewSCV(unit.getPosition());
				if(newBase != null){
					newBase.addMiner(claim);
				} else {
					needNewBase = true;
					Claim c = getClaim(unit.getID());
					if(c != null) c.free();
				}
			} else {
				Claim c = getClaim(unit.getID());
				if(c != null) c.free();
			}
		}
		
		if(KaonBot.getGame().getFrameCount() % 100 == 0 && needNewBase){
			incrementPriority(getVolitility(), false);
			needNewBase = false;
		}
		newUnits.clear();
	}

	protected class Base{
		BaseLocation location;
		double gdFromStart;
		double gdFromEnemy;
		private ArrayList<Unit> mins = new ArrayList<Unit>();
		Unit gas;
		Unit extractor = null;
		Unit cc = null;
		boolean active = false;
		ArrayList<Miner> miners = new ArrayList<Miner>();
		ArrayList<Miner> gasers = new ArrayList<Miner>();
		
		protected Base(BaseLocation location, BaseLocation start)
		{
			this.location = location;
			gdFromStart = BWTA.getGroundDistance(location.getTilePosition(), start.getTilePosition());
			
			List<BaseLocation> baseLocations = BWTA.getStartLocations();
			double distance = 0;
			for(BaseLocation bL: baseLocations){
				if(!bL.getPoint().equals(start.getPoint())) {
					distance += BWTA.getGroundDistance(bL.getTilePosition(), location.getTilePosition());
				}
			}
			
			gdFromEnemy = distance / baseLocations.size() - 1;
		}
		
		protected void addMinerals(Unit unit){
			mins.add(unit);
		}
		
		protected boolean addMiner(Claim unit){
			if(mins.size() == 0 && extractor == null){
				return false;
			}
			if(extractor != null && extractor.exists() && gasers.size() < 3){
				gasers.add(new Miner(unit, extractor));
				return true;
			} else if(mins.size() > 0){
				miners.add(new Miner(unit, mins.get((miners.size() + 1) % mins.size()))); //TODO implement mineral lock
				return true;
			}
			
			return false;
		}

		protected int requiredMiners(){
			if(cc == null || !cc.exists()){
				return 0;
			}
			int gas = 0;
			if(extractor != null && extractor.exists()){
				gas = 3;
			}
			
			return gas + mins.size() * 2 + mins.size() / 2 - miners.size();
		}
		
		protected List<Unit> update(){
			LinkedList<Unit> freeUnits = new LinkedList<Unit>();
			
			// check if CC exists
			if(cc == null || !cc.exists()){
				if(cc != null && location.equals(KaonBot.mainPosition))
				{
					findNewMainBase();
				}
				cc = null;
				for(Miner m: miners){
					if(m.getUnit().exists()){
						freeUnits.add(m.getUnit());
					}
				}
				miners.clear();
				for(Miner m: gasers){
					if(m.getUnit().exists()){
						freeUnits.add(m.getUnit());
					}
				}
				gasers.clear();
				return freeUnits;
			}
			
			// check all mineral patches TODO: check gas
			LinkedList<Miner> toRemove = new LinkedList<Miner>();
			Iterator<Unit> it = mins.iterator();
			while(it.hasNext()){
				Unit min = it.next();
				if(min.getResources() < 10){
					it.remove();
					incrementPriority(getVolitility(), false);

					//check miners to see if anyone is assigned to this patch
					for(Miner m: miners){
						if(m.resource == min){
							freeUnits.add(m.getUnit());
							toRemove.add(m);
						}
					}
				}
			}
			
			// run SCV updates, remove if they request
			for(Miner m : miners){
				if(m.update()){
					if(m.getUnit().exists()){
						freeUnits.add(m.getUnit());
						toRemove.add(m);
					}
					else{
						toRemove.add(m);
					}
				}
			}
			for(Miner m : gasers){
				if(m.update()){
					if(m.getUnit().exists()){
						freeUnits.add(m.getUnit());
						toRemove.add(m);
					}
					else{
						toRemove.add(m);
					}
				}
			}
			
			
			if(requiredMiners() < 0){
				for(int i = miners.size() - requiredMiners(); i < miners.size(); i++)
				{
					freeUnits.add(miners.get(i).getUnit());
					toRemove.add(miners.get(i));
				}
			}
			
			for(Miner m: toRemove){
				miners.remove(m);
			}
			for(Unit u: freeUnits){
				if(getClaim(u.getID()) != null){
					getClaim(u.getID()).free();
				}
			}
			return freeUnits;
		}
	}
	
	protected class Miner extends Behavior{

		private Unit resource;
		private UnitType resourceType;
		private boolean returning = false;
		private final int MICRO_LOCK = 10; //num frames to skip between micro actions
		private int microCount = 0; 
		
		public Miner(Claim miner, Unit resource){
			super(miner);
			this.resource = resource;
			this.resourceType = resource.getType();
			getUnit().gather(resource);
}
		
		public UnitType getResourceType(){
			return resourceType;
		}
		
		@Override
		public boolean update() {
			if(microCount < MICRO_LOCK)
			{
				microCount++;
				return false;
			}
			
			if(KaonBot.defenseManager.needEmergencyDefenders()){
				return true;
			}
			
			Order order = getUnit().getOrder();
			if(order == Order.MiningMinerals || order == Order.HarvestGas || order == Order.MoveToGas
					|| getUnit().isCarryingMinerals() || getUnit().isCarryingGas()
					|| order == Order.WaitForMinerals || order == Order.WaitForGas)
			{
				touchClaim();
				microCount++;
				return false;
			}

			microCount = 0;
			
			return resource.getResources() < 20 || !getUnit().exists();
		}
	}

	@Override
	public void displayDebugGraphics(Game game) {
		super.displayDebugGraphics(game);
		for(Base b: bases){
			if(b.cc != null){
//				game.drawTextMap(b.cc.getPosition(), "Patches: " + b.mins.size() + 
//													 "\nWorkers: " + b.miners.size() + 
//													 "\nonGas: " + b.gasers.size() +
//													 "\nNeed: " + b.requiredMiners());
				
				for(Miner m: b.miners){
					game.drawLineMap(m.resource.getPosition(), m.getUnit().getPosition(), debugColor);
					game.drawTextMap(m.resource.getPosition(), m.resource.getResources() + "/" + m.resource.getInitialResources());
					game.drawTextMap(m.getUnit().getPosition(), m.getUnit().getOrder().toString());
				}
				if(b.gas != null) game.drawLineMap(b.cc.getPosition(), b.gas.getPosition(), new Color(100, 200, 100));
			}
		}
	}
}
