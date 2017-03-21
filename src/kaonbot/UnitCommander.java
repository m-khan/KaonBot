package kaonbot;
import java.util.ArrayList;
import java.util.List;

import bwapi.Game;
import bwapi.Unit;

public interface UnitCommander {
	public void assignNewUnit(Claim claim);
	public void assignNewUnitBehaviors();
	public ArrayList<Double> claimUnits(List<Unit> unitList);
	public List<Claim> getAllClaims();
	public void freeUnits();
	public String getName();
	public void displayDebugGraphics(Game game);
}
