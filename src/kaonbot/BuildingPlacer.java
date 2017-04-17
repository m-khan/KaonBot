package kaonbot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bwapi.Color;
import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;


public class BuildingPlacer {

	private static BuildingPlacer buildingPlacer = new BuildingPlacer();
	private static Map<String, TilePosition> buildCache = new HashMap<String, TilePosition>();
	private static final int NULL_CACHE_RECALC_CHANCE = 1000;
	private Game game;
	private boolean[][] reservationMap;
	private Color[][] reservationColors;
	
	private BuildingPlacer(){
		game = KaonBot.mirror.getGame();
		reservationMap = new boolean[game.mapWidth()][game.mapHeight()];
		reservationColors = new Color[game.mapWidth()][game.mapHeight()];
	}
	
	public void clearCache(){
		//buildCache.clear();
	}
	
	public void reserve(TilePosition p, UnitType building, Color color){
		if(!building.isBuilding()){
			KaonBot.print("WARNING - tried to reserve and non-unit " + p.getPoint());
			return;
		}
		
		for(int x = p.getX(); x < p.getX() + building.tileWidth(); x++){
			for(int y = p.getY(); y < p.getY() + building.tileHeight(); y++){
				if(!reservationMap[x][y]){
					reservationMap[x][y] = true;
					reservationColors[x][y] = color;
				}
			}
		}
	}

	public void free(TilePosition p, UnitType building) {
		if(!building.isBuilding()){
			KaonBot.print("WARNING - tried to free a non-building unit " + p.getPoint());
			return;
		}
		
		for(int x = p.getX(); x < p.getX() + building.tileWidth(); x++){
			for(int y = p.getY(); y < p.getY() + building.tileHeight(); y++){
				reservationMap[x][y] = false;
				reservationColors[x][y] = null;
			}
		}
	}
	
	public void reserve(Unit u){
		try {
			mark(u, true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void free(Unit u){
		mark(u, false);
	}
	
	private void mark(Unit u, boolean marker){
		TilePosition tp = u.getTilePosition();
		int x = tp.getX();
		int y = tp.getY();
		
		UnitType type = u.getType();
		
		int tileWidth = type.tileWidth();
		int tileHeight = type.tileHeight();
		
		// reserve extra space around resource things
		if(type.isMineralField() || type == UnitType.Resource_Vespene_Geyser){
			x -= 2;
			y -= 2;
			tileWidth += 4;
			tileHeight += 4;
		}
		
		for(int i = 0; i < tileWidth; i++){
			for(int j = 0; j < tileHeight; j++){
				if(x + i < game.mapWidth() && x + i >= 0 && y + j < game.mapHeight() && y + j >= 0) {
					reservationMap[x + i][y + j] = marker;
				}
			}
		}
	}
	
	public void drawReservations(){
		for(int i = 0; i < reservationMap.length; i++){
			for(int j = 0; j < reservationMap[i].length; j++)
			{
				if(reservationMap[i][j]){
					Color color;
					if(reservationColors[i][j] != null){
						color = reservationColors[i][j];
						game.drawBoxMap(i * 32, j * 32, i * 32 + 32, j * 32 + 32, color, false);
					} 
					else {
//						color = new Color(0, 0, 0);
//						game.drawBoxMap(i * 32, j * 32, i * 32 + 32, j * 32 + 32, color, false);
					}
				}
			}
		}
	}
	
	public Unit getSuitableBuilder(TilePosition position){
		List<Claim> claimList = KaonBot.getAllClaims();
		Claim c = KaonUtils.getClosestClaim(position.toPosition(), claimList, UnitType.Terran_SCV, 
				KaonBot.SCV_BUILDER_COMMANDEER_PRIORITY, null);
		if(c != null){
			return c.unit;
		}
		return null;
	}

	public TilePosition getBuildTile(UnitType buildingType, TilePosition aroundTile){
		Unit builder = getSuitableBuilder(aroundTile);
		if(builder == null){
			KaonBot.print("Unable to find suitable builder for " + buildingType.toString(), true);
			return null;
		}

		String sig = buildingType.toString() + aroundTile.getX() + aroundTile.getY();
		
		if(buildCache.containsKey(sig)){
			TilePosition spot = buildCache.get(sig);
			
			if(spot == null){
				KaonBot.print("Null cache entry for " + buildingType.toString(), true);
				if(KaonUtils.getRandom().nextInt(NULL_CACHE_RECALC_CHANCE) != 1){
					return null;
				}
				KaonBot.print("Null cache entry will be recalculated", false);
			}
			
//			KaonBot.print(sig + ": " + buildCache.get(sig));
//			KaonBot.print("reserved: " + spotIsReserved(spot.getX(), spot.getY(), buildingType));
//			KaonBot.print("can build: " + game.canBuildHere(spot, buildingType, builder, false));
			if(!spotIsReserved(spot.getX(), spot.getY(), buildingType)) {
				if(game.canBuildHere(buildCache.get(sig), buildingType, builder, false)){
					return buildCache.get(sig);
				}
			}
		}
		return getBuildTile(builder, buildingType, aroundTile);
	}
	
	// Returns a suitable TilePosition to build a given building type near 
	// specified TilePosition aroundTile, or null if not found. (builder parameter is our worker)
	private TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
		//System.err.println("getBuildTile()");
		
		TilePosition ret = null;
		int maxDist = 3;
		int stopDist = 20;
		
		String cacheSig = buildingType.toString() + aroundTile.getX() + aroundTile.getY();
		
		while ((maxDist < stopDist) && (ret == null)) {
			for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; i++) {
				for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; j++) {
					if (!spotIsReserved(i, j, buildingType)) {
						if (game.canBuildHere(new TilePosition(i, j), buildingType, builder, false)) {
							TilePosition toReturn = new TilePosition(i, j);
							buildCache.put(cacheSig, toReturn);
							return toReturn;
						}
					}
				}
			}
			maxDist += 2;
		}
		
		if (ret == null) {
			buildCache.put(cacheSig, null);
			KaonBot.econManager.findNewMainBase();
			KaonBot.print("Unable to find suitable build position for " + buildingType.toString(), true);
		}
		return ret;
	}
	
	public boolean spotIsReserved(int xPos, int yPos, UnitType buildingType){
		for(int x = xPos - 1; x < xPos + buildingType.tileWidth() + 2; x++){
			for(int y = yPos - 1; y < yPos + buildingType.tileHeight() + 2; y++){
				//System.out.println(x + ", " + y + ": " + reservationMap[x][y]);
				if(x < game.mapWidth() && x >= 0 && y < game.mapHeight() && y >= 0) {
					if(reservationMap[x][y]){
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public static TilePosition getTilePosition(int px, int py){
		TilePosition tp = new TilePosition(px / 32, py / 32);
		tp.makeValid();
		return tp;
	}
	
	public static TilePosition getTilePosition(Unit u){
		return getTilePosition(u.getTop(), u.getLeft());
	}
	
	public static BuildingPlacer getInstance(){
		return buildingPlacer;
	}
	
	public boolean canBuildHere(TilePosition position, UnitType b){
		if (!game.canBuildHere(position, b))
			return false;
		return true;
	}

}
