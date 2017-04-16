package kaonbot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwta.BWTA;
import bwta.BaseLocation;

public class KaonBot extends DefaultBWListener {

	public static boolean debug = true;
    public static Mirror mirror = new Mirror();

    public static double SCV_COMMANDEER_BUILDING_MULTIPLIER = 1000.0;
    
    private static Game game;

    // Cache values that don't change during frame
    private static List<Unit> allUnits;
    private static int supply = 0;
    private static int minerals = 0;
    private static int gas = 0;
    
    private static Player self;
    
    private static ArrayList<Manager> managerList = new ArrayList<Manager>();
    private static ArrayList<TempManager> tempManagers = new ArrayList<TempManager>();
    private static Map<Integer, Unit> discoveredEnemies = new HashMap<Integer, Unit>();
    private static BaseLocation startPosition;
    public static BaseLocation mainPosition;
    private ArrayList<Unit> unclaimedUnits = new ArrayList<Unit>();
    private Map<Integer, Claim> masterClaimList = new HashMap<Integer, Claim>();
    public static BuildingPlacer bpInstance;
    public static ProductionQueue pQueue;
    public static EconomyManager econManager;
    public static DepotManager depotManager;
    public static RushManager rushManager;
    public static DefenseManager defenseManager;
    public static ScoutManager scoutManager;
    public static BioUpgradeManager bioUpgradeManager;
    
    private final int STALE_CLAIM = 100;
    private final int GARBAGE_COLLECT_RATE = 100;

    public static Game getGame(){
    	// TODO find a better solution
    	return game;
    }
    
    public static List<Unit> getAllUnits(){
    	return allUnits;
    }
    
    public static int getSupply(){
    	return supply; 
    }
    
    public static int getMinerals(){
    	return minerals;
    }
    
    public static int getGas(){
    	return gas;
    }
    
    public static void showMessage(String message){
    	//game.printf(message);
    }
    
    public static boolean isFriendly(Unit u){
    	return u.getPlayer() == self;
    }
    
    public static boolean isEnemy(Unit u){
    	return self.isEnemy(u.getPlayer());
    }
    
    public static boolean hasResearched(TechType tech){
    	return self.hasResearched(tech);
    }
    
    public static int getUpgradeLevel(UpgradeType up){
    	return self.getUpgradeLevel(up);
    }
    
    public static void print(String message, boolean error){
    	try {
			if(error) System.err.println(game.getFrameCount() + " " + message);
			else System.out.println(game.getFrameCount() + " " + message);
    	} catch (Exception e) {
			System.err.println("ERROR PRINTING MESSAGE");
		}
    }
    
    public static void print(String message){
    	print(message, false);
    }
    
    public static List<Claim> getAllClaims(){
    	List<Claim> allClaims = new ArrayList<Claim>();
    	for(Manager m: managerList){
    		allClaims.addAll(m.getAllClaims());
    	}
    	return allClaims;
    }
    
    public static Map<Integer, Unit> discoveredEnemies(){
    	return discoveredEnemies;
    }
    
    public static BaseLocation getStartPosition(){
    	return startPosition;
    }
    
    public static BaseLocation getCurrentMainBase(){
    	return mainPosition;
    }
    
    public static void addTempManager(TempManager m){
    	tempManagers.add(m);
    }
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onEnd(boolean b){
    	super.onEnd(b);
    	System.exit(0);
    }
    
    @Override
    public void onStart() {
    	try{
	        game = mirror.getGame();
    		//showMessage("onStart()");
	        game.enableFlag(1);
	        self = game.self();
	        pQueue = new ProductionQueue(self);
	        bpInstance = BuildingPlacer.getInstance();
	
	        game.setLocalSpeed(15);
	        
	        //Use BWTA to analyze map
	        //This may take a few minutes if the map is processed first time!
	        BWTA.readMap();
	        BWTA.analyze();

	        Random r = new Random();
	        
	        startPosition = BWTA.getStartLocation(self);
	        mainPosition = startPosition;
	        econManager = new EconomyManager(1 - r.nextDouble() * 0.5, 0.1 + r.nextDouble());
	        depotManager = new DepotManager(1 - r.nextDouble() * 0.5, 0.1 + r.nextDouble(), econManager, self);
	        rushManager = new RushManager(r.nextDouble(), r.nextDouble());
	        defenseManager = new DefenseManager(r.nextDouble(), r.nextDouble());
	        scoutManager = new ScoutManager(r.nextDouble(), r.nextDouble());
	        bioUpgradeManager = new BioUpgradeManager(r.nextDouble() * 0.5, r.nextDouble());
	        
	        game.sendTextEx(false, "ECON: " + econManager.usePriority() + "/" + econManager.getVolitility());
	        game.sendTextEx(false, "DEPT: " + depotManager.usePriority() + "/" + depotManager.getVolitility());
	        game.sendTextEx(false, "ATTK: " + rushManager.usePriority() + "/" + rushManager.getVolitility());
	        game.sendTextEx(false, "DEFN: " + defenseManager.usePriority() + "/" + defenseManager.getVolitility());
	        game.sendTextEx(false, "DEFN: " + scoutManager.usePriority() + "/" + scoutManager.getVolitility());
	        game.sendTextEx(false, "DEFN: " + bioUpgradeManager.usePriority() + "/" + bioUpgradeManager.getVolitility());

	        KaonBot.print("ECON: " + econManager.usePriority() + "/" + econManager.getVolitility());
	        KaonBot.print("DEPT: " + depotManager.usePriority() + "/" + depotManager.getVolitility());
	        KaonBot.print("ATTK: " + rushManager.usePriority() + "/" + rushManager.getVolitility());
	        KaonBot.print("DEFN: " + defenseManager.usePriority() + "/" + defenseManager.getVolitility());
	        KaonBot.print("DEFN: " + scoutManager.usePriority() + "/" + scoutManager.getVolitility());
	        KaonBot.print("DEFN: " + bioUpgradeManager.usePriority() + "/" + bioUpgradeManager.getVolitility());

	        managerList.add(econManager);
	        managerList.add(depotManager);
	        managerList.add(rushManager);
	        managerList.add(defenseManager);
	        managerList.add(scoutManager);
	        managerList.add(bioUpgradeManager);
	        
	        for(Manager m: managerList){
	        	m.init(game);
	        }
	        
	//        BWTA.getBaseLocations();
	        //unclaimedUnits.addAll(self.getUnits());
	        
	        for(Unit u : game.getAllUnits()){
	        	if(u.getType().isBuilding() || u.getType().isResourceDepot()) bpInstance.reserve(u);
	        }
    	}catch(Exception e){
    		showMessage("Error in onStart(): " + e);
    		e.printStackTrace();
    	}
    }


    @Override
    public void onUnitComplete(Unit unit) {
    	try{
//    		showMessage("onUnitComplete()");
        	if(unit.getPlayer() == self && unit.getType().isBuilding()){
        		for(Manager m: managerList){
        			m.handleCompletedBuilding(unit, unit.getPlayer() == self);
        		}
        	}
    	}catch(Exception e){
    		showMessage("Error in onUnitComplete(): " + e);
    		e.printStackTrace();
    	}
    }
    
    @Override
	public void onUnitDiscover(Unit unit){
    	try{
    		if(unit.getType().isBuilding()){
    			bpInstance.reserve(unit);
    		}
    		if(discoveredEnemies.put(unit.getID(), unit) == null) {
	    		//showMessage("onUnitDiscover()");
		    	if(unit.getType().isBuilding()) bpInstance.reserve(unit);
		
				for(Manager manager: managerList){
					manager.handleNewUnit(unit, unit.getPlayer() == self, unit.getPlayer().isEnemy(self));
				}
    		}
    	}catch(Exception e){
    		showMessage("Error in onUnitDiscover(): " + e);
    		e.printStackTrace();
    	}
    }
    
    @Override
    public void onUnitDestroy(Unit unit){
    	try{
    		UnitType type = unit.getType();
    		
    		if(type.isBuilding()) bpInstance.free(unit);

    		boolean friendly = unit.getPlayer() == self;
    		boolean enemy = self.isEnemy(unit.getPlayer());
    		
    		// DO THIS BEFORE CLAIMS ARE REMOVED
    		for(Manager m: managerList){
    			m.handleUnitDestroy(unit, friendly, enemy);
    		}
    		
    		if(friendly)
    		{
	    		//showMessage("onUnitDestroy()");
	    		KaonBot.print("Unit Destroyed: " + type);
	    		Claim toCleanup = masterClaimList.remove(unit.getID());
    		
    			if(toCleanup != null){
    			// notify the manager the unit has been "commandeered" by the reaper
    				toCleanup.commandeer(null, Double.MAX_VALUE);
    			}
    			
    			if(type.isBuilding()){
    				econManager.findNewMainBase();
    			}
    			
    		}
    		else if(enemy){
    			discoveredEnemies.remove(unit.getID());
    		}
    		
    	}catch(Exception e){
    		showMessage("Error in onUnitDestroy(): " + e);
    		e.printStackTrace();
    	}
    }
    
    @Override
    public void onFrame() {
    	try{
    		//showMessage("onFrame()");
    		allUnits = game.getAllUnits();
    		supply = self.supplyUsed();
    		minerals = self.minerals();
    		gas = self.gas();
    		
    		runFrame();
    	} catch(Exception e){
    		showMessage("Error in onFrame(): " + e);
    		e.printStackTrace();
    	}
    	game.drawTextScreen(0, 0, "FRAME: " + game.getFrameCount());
    	game.drawTextScreen(200, 0, "APM: " + game.getAPM());
    }
    
    public void runFrame(){
        //game.setTextSize(10);

//    	KaonBot.print("FRAME: " + game.getFrameCount());
    	
    	ggCheck();
    	
    	if(game.getFrameCount() % GARBAGE_COLLECT_RATE == 0){
    		for(Manager m: managerList){
    			m.garbageCollect();
    		}
    	}
    	
    	StringBuilder output = new StringBuilder("===MANAGERS===\n");
    	for (Manager manager : managerList){
    		output.append(manager.getName()).append(": \n").append(manager.getStatus());
    	}

    	output.append("TEMP MANAGERS: " + tempManagers.size() + "\n");
    	Iterator<TempManager> it = tempManagers.iterator();
    	while(it.hasNext()){
    		TempManager next = it.next();
    		if(next.isDone()){
    			next.freeUnits();
    			it.remove();
    		}
    		else
    		{
    			next.runFrame();
    		}
    	}
    	
    	handleUnclaimedUnits(output);
    	for (Manager manager : managerList){
    		manager.assignNewUnitBehaviors();
    		manager.runFrame();
    	}

        game.drawTextScreen(400, 10, output.toString());

        pQueue.clear();
        for(Manager m: managerList){
        	pQueue.addAll(m.getProductionRequests());
        }

        String out = pQueue.processQueue();
        game.drawTextScreen(10, 10, out);

        displayDebugGraphics();
    }
    
    public void ggCheck(){
    	if(supply == 0 && getMinerals() < 50){
    		game.sendText("gg");
    		game.leaveGame();
    	}
    }
    
    public void handleUnclaimedUnits(StringBuilder output){
    	// Give the managers a chance to claim new units
    	
    	Iterator<Map.Entry<Integer, Claim>> it = masterClaimList.entrySet().iterator();
    	while(it.hasNext()){
    		Map.Entry<Integer, Claim> pair = (Map.Entry<Integer, Claim>) it.next();
    		if(game.getFrameCount() - pair.getValue().getLastTouched() > STALE_CLAIM){
    			it.remove();
    			pair.getValue().free();
    		}
    	}
    	
    	for(Unit u: self.getUnits()){
    		if(u.exists() && u.isCompleted() && !u.getType().isBuilding() &&
    				!masterClaimList.containsKey(u.getID())){
    			unclaimedUnits.add(u);
    		}
    	}
    	
    	output.append("Unclaimed Units: " + unclaimedUnits.size() + "\n");
//    	for (Unit unit : unclaimedUnits){
//    		output.append(unit.getType().toString()).append(": ").append(unit.getPosition().getPoint().toString()).append("\n");
//    	}
    	
    	// Make sure all the units in the claims list exist
    	Iterator<Unit> iter = unclaimedUnits.iterator();
    	while(iter.hasNext()){
    		if(!iter.next().exists()){
    			iter.remove();
    		}
    	}
    	
    	int num_units = unclaimedUnits.size();
    	ArrayList<Claim> topClaims = new ArrayList<Claim>(num_units);
    	for(int i = 0; i < num_units; i++){
    		topClaims.add(new Claim(null, 0.0, unclaimedUnits.get(i)));
    	}
    	for (Manager manager : managerList){
    		List<Double> claims = manager.claimUnits(unclaimedUnits);

    		if(claims != null){
	    		for(int i = 0; i < num_units; i++){
	    			Double newClaim = claims.get(i);
	        		if(topClaims.get(i).getPriority() < newClaim){
	        			topClaims.set(i, new Claim(manager, newClaim, unclaimedUnits.get(i)));
	        		}
	        	}
    		}
    	}

    	// Resolve all claims and assign the new units
    	unclaimedUnits.clear(); // this list will be repopulated with the leftovers
    	for(Claim claim : topClaims){
    		if(claim.getCommander() != null){
    			claim.addOnCommandeer(claim.new CommandeerRunnable() {
					@Override
					public void run() {
						if(newManager == null){
							masterClaimList.remove(claim.unit.getID());
						}
					}
				});
    			
    			claim.getCommander().assignNewUnit(claim);
    			Claim duplicate = masterClaimList.put(claim.unit.getID(), claim);
    			if(duplicate != null){
    				System.err.println("WARNING - Duplicate claim: " + claim);
    				duplicate.free(); // attempt to clean up the old claim
    			}
    		}
    	}
    }
    
    public void displayDebugGraphics(){
    	//TODO add flag
    	
    	bpInstance.drawReservations();
    	for(Manager m: managerList){
    		m.displayDebugGraphics(game);
    	}
    	for(UnitCommander c: tempManagers){
    		c.displayDebugGraphics(game);
    	}
//    	for(Claim c: getAllClaims()){
//    		game.drawTextMap(c.unit.getPosition(), c.getCommander().toString());
//    	}
    }
    
    public static void main(String[] args) {
    	try{
            new KaonBot().run();
    	}catch(Exception e){
    		e.printStackTrace();
    	}
    }
}