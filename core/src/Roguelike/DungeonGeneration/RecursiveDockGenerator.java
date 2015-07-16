package Roguelike.DungeonGeneration;

import java.util.HashSet;
import java.util.Random;

import PaulChew.Pnt;
import PaulChew.Triangle;
import PaulChew.Triangulation;
import Roguelike.DungeonGeneration.DungeonFileParser.Symbol;
import Roguelike.Entity.EnvironmentEntity;
import Roguelike.Levels.Level;
import Roguelike.Pathfinding.Pathfinder;
import Roguelike.Pathfinding.PathfindingTile;
import Roguelike.Tiles.GameTile;
import Roguelike.Tiles.TileData;

import com.badlogic.gdx.utils.Array;

public class RecursiveDockGenerator
{
	public class Room
	{
		public int width;
		public int height;
		
		public int x;
		public int y;
		
		//public boolean flipVertical = ran.nextBoolean();
		//public boolean flipHorizontal = ran.nextBoolean();
		
		public GenerationTile[][] roomContents;
		
		public void rotate()
		{
			GenerationTile[][] newContents = new GenerationTile[height][width];
			
			for (int x = 0; x < width; x++)
			{
				for (int y = 0; y < height; y++)
				{
					newContents[y][x] = roomContents[x][y];
				}
			}
			
			roomContents = newContents;
			
			int temp = height;
			height = width;
			width = temp;
		}
		
		public void generateRoomContents()
		{
			roomContents = new GenerationTile[width][height];
			
			for (int x = 0; x < width; x++)
			{
				for (int y = 0; y < height; y++)
				{
					roomContents[x][y] = new GenerationTile();
					roomContents[x][y].tileType = TileType.FLOOR;
					
					if (x == 0 || x == width-1 || y == 0 || y == height-1)
					{
						roomContents[x][y].pathfindType = PathfindType.WALL;
						roomContents[x][y].tileType = TileType.WALL;
					}
				}
			}
			
			// Sides
			//  1
			// 0 2
			//  3
			
			int doorSide = ran.nextInt(4);
			
			if (doorSide == 0)
			{
				int x = 0;
				int y = 1 + ran.nextInt(height-2);
				
				roomContents[x][y].pathfindType = PathfindType.NONE;
				roomContents[x][y].tileType = TileType.FLOOR;
			}
			else if (doorSide == 1)
			{
				int x = 1 + ran.nextInt(width-2);
				int y = 0;
				
				roomContents[x][y].pathfindType = PathfindType.NONE;
				roomContents[x][y].tileType = TileType.FLOOR;
			}
			else if (doorSide == 2)
			{
				int x = width-1;
				int y = 1 + ran.nextInt(height-2);
				
				roomContents[x][y].pathfindType = PathfindType.NONE;
				roomContents[x][y].tileType = TileType.FLOOR;
			}
			else if (doorSide == 3)
			{
				int x = 1 + ran.nextInt(width-2);
				int y = height-1;
				
				roomContents[x][y].pathfindType = PathfindType.NONE;
				roomContents[x][y].tileType = TileType.FLOOR;
			}
		}
	}
	
	final GenerationTile[][] tiles;
	private Random ran = new Random();
	
	private int width;
	private int height;
	
	private int minPadding = 1;
	private int maxPadding = 4;
	
	private int minRoomSize = 6;
	private int maxRoomSize = 15;
	
	private int paddedMinRoom = minRoomSize + minPadding*2;
	
	private Array<Room> toBePlaced = new Array<Room>();
	
	private Array<Room> placedRooms = new Array<Room>();
	
	public RecursiveDockGenerator(int width, int height)
	{
		this.width = width;
		this.height = height;
		
		this.tiles = new GenerationTile[width][height];
		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				tiles[x][y] = new GenerationTile();
			}
		}
	}
	
	public Level getLevel()
	{
		DungeonFileParser dfp = DungeonFileParser.load("level1"); 
		
		TileData wallData = dfp.sharedSymbolMap.get('#').tileData;
		TileData floorData = dfp.sharedSymbolMap.get('.').tileData;
		
		GameTile[][] actualTiles = new GameTile[width][height];
		Level level = new Level(actualTiles);
		
		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				GenerationTile oldTile = tiles[x][y];

				GameTile newTile = new GameTile
				(
					x, y,
					level,
					oldTile.tileType == TileType.WALL ? wallData : floorData
				);
				
				if (oldTile.tileType == TileType.DOOR)
				{
					newTile.addEnvironmentEntity(EnvironmentEntity.CreateDoor());
				}
				
				actualTiles[x][y] = newTile;
			}
		}
		
		for (Room room : placedRooms)
		{			
			GameTile tile = actualTiles[room.x+room.width/2][room.y+room.height/2];
			tile.addEnvironmentEntity(EnvironmentEntity.CreateTransition());
		}
		
		return level;
	}
	
	public void generate() 
	{
		DungeonFileParser dfp = DungeonFileParser.load("level1");
		
		Room room = new Room();
		dfp.rooms.get(0).fillRoom(room);
		toBePlaced.add(room);
		
		room = new Room();
		dfp.rooms.get(0).fillRoom(room);
		toBePlaced.add(room);
		
		partition(1, 1, width-2, height-2);
		
		markRooms();

		connectRooms();	
	}
	
	public void partition(int x, int y, int width, int height)
	{
		int padX = Math.min(ran.nextInt(maxPadding-minPadding)+minPadding, (width-minRoomSize)/2);
		int padY = Math.min(ran.nextInt(maxPadding-minPadding)+minPadding, (height-minRoomSize)/2);
		
		int padX2 = padX * 2;
		int padY2 = padY * 2;
		
		// get the room to be placed
		Room room = null;
	
		// if the predefined rooms array has items, then try to pick one from it
		if (toBePlaced.size > 0)
		{
			// Array of indexes to be tried, stops duplicate work
			Array<Integer> indexes = new Array<Integer>();
			for (int i = 0; i < toBePlaced.size; i++) { indexes.add(i); }
			
			while (room == null && indexes.size > 0)
			{
				int index = indexes.removeIndex(ran.nextInt(indexes.size));
				
				Room testRoom = toBePlaced.get(index);
				
				// Check if the room fits, either at the default rotation or at 90 degrees
				boolean fitsVertical = testRoom.width + padX2 <= width && testRoom.height + padY2 <= height;
				boolean fitsHorizontal = testRoom.height + padX2 <= width && testRoom.width + padY2 <= height;
				
				if (fitsVertical || fitsHorizontal)
				{
					room = testRoom;
					toBePlaced.removeIndex(index);
					
					// if it fits on both directions, randomly pick one
					if (fitsVertical && fitsHorizontal)
					{
						if (ran.nextBoolean())
						{
							room.rotate();
						}
					}
					else if (fitsHorizontal)
					{
						room.rotate();
					}
				}
			}
		}
		
		// failed to find a suitable predefined room, so create a new one
		if (room == null)
		{
			int roomWidth = Math.min(ran.nextInt(maxRoomSize-minRoomSize)+minRoomSize, width - padX2);
			int roomHeight = Math.min(ran.nextInt(maxRoomSize-minRoomSize)+minRoomSize, height - padY2);
			
			room = new Room();
			room.width = roomWidth;
			room.height = roomHeight;
			
			room.generateRoomContents();
		}
		
		placedRooms.add(room);

		// pick corner

		// possible sides:
		// 0 1
		// 2 3
		int side = ran.nextInt(4);
		
		// Position room at side
		if (side == 0)
		{
			room.x = x + padX;
			room.y = y + padY;
		}
		else if (side == 1)
		{
			room.x = ( x + width ) - ( room.width + padX );
			room.y = y + padY;
		}
		else if (side == 2)
		{
			room.x = x + padX;
			room.y = ( y + height ) - ( room.height + padY );
		}
		else
		{
			room.x = ( x + width ) - ( room.width + padX );
			room.y = ( y + height ) - ( room.height + padY );
		}

		// split into 2 remaining rectangles and recurse
		if (side == 0)
		{
			// r1
			// 22
			{
				int nx = room.x + room.width + padX;
				int ny = y;
				int nwidth = x + width - nx;
				int nheight = room.height + padY2;
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
			
			{
				int nx = x;
				int ny = room.y + room.height + padY;
				int nwidth = width;
				int nheight = y + height - ny;
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
		}
		else if (side == 1)
		{
			// 1r
			// 12
			{
				int nx = x;
				int ny = y;
				int nwidth = width - (room.width + padX2);
				int nheight = height;
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
			
			{
				int nx = room.x - padX;
				int ny = room.y + room.height + padY;
				int nwidth = room.width + padX2;
				int nheight = (y + height) - ny;
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
		}
		else if (side == 2)
		{
			// 12
			// r2
			{
				int nx = x;
				int ny = y;
				int nwidth = room.width + padX2;
				int nheight = height - (room.height + padY2);
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
			
			{
				int nx = x + room.width + padX2;
				int ny = y;
				int nwidth = (x + width) - nx;
				int nheight = height;
				
				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
		}
		else
		{
			// 22
			// 1r
			{
				int nx = x;
				int ny = room.y - padY;
				int nwidth = width - (room.width + padX2);
				int nheight = (y + height) - ny;

				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}

			{
				int nx = x ;
				int ny = y;
				int nwidth = width;
				int nheight = height - (room.height + padY2);

				if (nwidth >= paddedMinRoom && nheight >= paddedMinRoom)
				{
					partition(nx, ny, nwidth, nheight);
				}
			}
		}
	}

	protected void connectRooms()
	{
		Array<Pnt> roomPnts = new Array<Pnt>();
		
		for (Room room : placedRooms)
		{
			for (int x = 0; x < room.width; x++)
			{
				for (int y = 0; y < room.height; y++)
				{
					if (x == 0 || x == room.width-1 || y == 0 || y == room.height-1)
					{
						GenerationTile tile = room.roomContents[x][y];
						if (tile.pathfindType != PathfindType.WALL)
						{
							Pnt p = new Pnt(x+room.x, y+room.y);
							roomPnts.add(p);
							
							System.out.println("Door at: "+p.coord(0)+","+p.coord(1));
						}
					}
				}
			}
		}

		Triangle initialTriangle = new Triangle(
				new Pnt(-10000, -10000),
				new Pnt(10000, -10000),
				new Pnt(0, 10000));
		Triangulation dt = new Triangulation(initialTriangle);
		
		for (Pnt p : roomPnts)
		{
			dt.delaunayPlace(p);
		}
		
		Array<Pnt[]> ignoredPaths = new Array<Pnt[]>();
		Array<Pnt[]> addedPaths = new Array<Pnt[]>();
		Array<Pnt[]> paths = new Array<Pnt[]>();
		
		for (Triangle tri : dt)
		{
			calculatePaths(paths, tri, ignoredPaths, addedPaths);
		}

		for (Pnt[] p : paths)
		{
			System.out.println("Path between: "+p[0].coord(0)+","+p[0].coord(1)+" -> "+p[1].coord(0)+","+p[1].coord(1));
			
			Pathfinder pathFind = new Pathfinder(tiles, (int)p[0].coord(0), (int)p[0].coord(1), (int)p[1].coord(0), (int)p[1].coord(1), false, new HashSet<String>());
			carveCorridor(pathFind.getPath());
		}
	}
	
	protected void carveCorridor(int[][] path)
	{		
		for (int[] pos : path)
		{
			GenerationTile t = tiles[pos[0]][pos[1]];
			
			t.tileType = TileType.FLOOR;
			t.pathfindType = PathfindType.CORRIDOR;
		}
		
		System.out.println(path.length);
	}
	
	protected void calculatePaths(Array<Pnt[]> paths, Triangle triangle, Array<Pnt[]> ignoredPaths, Array<Pnt[]> addedPaths)
	{
		Pnt[] vertices = triangle.toArray(new Pnt[0]);
		
		int ignore = 0;
        double dist = 0;
        
        dist = Math.pow(2, vertices[0].coord(0)-vertices[1].coord(0))+Math.pow(2, vertices[0].coord(1)-vertices[1].coord(1));
        
        double temp = Math.pow(2, vertices[0].coord(0)-vertices[2].coord(0))+Math.pow(2, vertices[0].coord(1)-vertices[2].coord(1));
        if (dist < temp)
        {
        	dist = temp;
        	ignore = 1;		
        }
        
        temp = Math.pow(2, vertices[1].coord(0)-vertices[2].coord(0))+Math.pow(2, vertices[1].coord(1)-vertices[2].coord(1));
        if (dist < temp)
        {
        	dist = temp;
        	ignore = 2;		
        }
        
        if (ignore != 0 && !checkIgnored(vertices[0], vertices[1], ignoredPaths) && !checkAdded(vertices[0], vertices[1], addedPaths))
        {
        	addPath(vertices[0], vertices[1], paths, ignoredPaths, addedPaths);
        }
        else
        {
        	ignoredPaths.add(new Pnt[]{vertices[0], vertices[1]});
        }
        
        if (ignore != 1 && !checkIgnored(vertices[0], vertices[2], ignoredPaths) && !checkAdded(vertices[0], vertices[2], addedPaths))
        {
        	addPath(vertices[0], vertices[2], paths, ignoredPaths, addedPaths);
        }
        else
        {
        	ignoredPaths.add(new Pnt[]{vertices[0], vertices[2]});
        }
        
        if (ignore != 2 && !checkIgnored(vertices[1], vertices[2], ignoredPaths) && !checkAdded(vertices[1], vertices[2], addedPaths))
        {
        	addPath(vertices[1], vertices[2], paths, ignoredPaths, addedPaths);
        }
        else
        {
        	ignoredPaths.add(new Pnt[]{vertices[1], vertices[2]});
        }
	}
	
    protected void addPath(Pnt p1, Pnt p2, Array<Pnt[]> paths, Array<Pnt[]> ignoredPaths, Array<Pnt[]> addedPaths)
    {
    	if (
    			p1.coord(0) < 0 || p1.coord(1) < 0 ||
    			p1.coord(0) >= width-1 || p1.coord(1) >= height-1 ||
    			p2.coord(0) < 0 || p2.coord(1) < 0 ||
    	    	p2.coord(0) >= width-1 || p2.coord(1) >= height-1
    	    	)
    	{
    		ignoredPaths.add(new Pnt[]{p1, p2});
    	}
    	else
    	{
        	addedPaths.add(new Pnt[]{p1, p2});
        	paths.add(new Pnt[]{p1, p2});
    	}
    }
	    
    protected boolean checkIgnored(Pnt p1, Pnt p2, Array<Pnt[]> ignoredPaths)
    {
    	for (Pnt[] p : ignoredPaths)
    	{
    		if (p[0].equals(p1) && p[1].equals(p2))
    		{
    			return true;
    		}
    		else if (p[0].equals(p2) && p[1].equals(p1))
    		{
    			return true;
    		}
    	}
    	return false;
    }
    
    protected boolean checkAdded(Pnt p1, Pnt p2, Array<Pnt[]> addedPaths)
    {
    	for (Pnt[] p : addedPaths)
    	{
    		if (p[0].equals(p1) && p[1].equals(p2))
    		{
    			return true;
    		}
    		else if (p[0].equals(p2) && p[1].equals(p1))
    		{
    			return true;
    		}
    	}
    	return false;
    }

    protected void markRooms()
	{
		for (Room room : placedRooms)
		{			
			for (int x = 0; x < room.width; x++)
			{
				for (int y = 0; y < room.height; y++)
				{
					tiles[room.x+x][room.y+y].pathfindType = room.roomContents[x][y].pathfindType;
					tiles[room.x+x][room.y+y].tileType = room.roomContents[x][y].tileType;
				}
			}
		}
	}
    
    public enum TileType
	{
		FLOOR,
		WALL,
		DOOR
	}
	
    public enum PathfindType
    {
    	NONE,
    	WALL,
    	CORRIDOR
    }
    
	public static class GenerationTile implements PathfindingTile
	{
		public Symbol symbol;
		
		public PathfindType pathfindType = PathfindType.NONE;
		
		public TileType tileType = TileType.WALL;
		
		@Override
		public boolean getPassable(HashSet<String> factions)
		{
			return pathfindType != PathfindType.WALL;
		}

		
		@Override
		public int getInfluence()
		{
			//if (pathfindType == PathfindType.CORRIDOR)
			//{
				return 0;
			//}
			//else
			//{
			//	return 5000;
			//}
		}
	}
}