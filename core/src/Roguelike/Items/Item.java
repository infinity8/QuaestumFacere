package Roguelike.Items;

import Roguelike.Ability.AbilityTree;
import Roguelike.Ability.ActiveAbility.ActiveAbility;
import Roguelike.Ability.IAbility;
import Roguelike.Ability.PassiveAbility.PassiveAbility;
import Roguelike.AssetManager;
import Roguelike.Entity.Entity;
import Roguelike.Entity.GameEntity;
import Roguelike.GameEvent.GameEventHandler;
import Roguelike.Global;
import Roguelike.Global.Statistic;
import Roguelike.Lights.Light;
import Roguelike.Sound.SoundInstance;
import Roguelike.Sprite.Sprite;
import Roguelike.Sprite.SpriteAnimation.MoveAnimation;
import Roguelike.Tiles.Point;
import Roguelike.UI.Seperator;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;
import exp4j.Helpers.EquationHelper;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.io.IOException;

public final class Item extends GameEventHandler
{
	/*
	 * IDEAS:
	 *
	 * Unlock extra power after condition (absorb x essence, kill x enemy)
	 */

	public String name = "";
	public String description = "";
	public Sprite icon;
	public Sprite hitEffect;
	public Array<EquipmentSlot> slots = new Array<EquipmentSlot>();
	public ItemCategory category;
	public String type = "";
	public boolean canStack;
	public int count = 1;
	public Light light;
	public boolean canDrop = true;
	public String dropChanceEqn;
	public AbilityTree ability;
	public WeaponDefinition wepDef;
	public int quality = 1;
	public int upgradeCount = 1;

	// ----------------------------------------------------------------------
	public Item()
	{

	}

	// ----------------------------------------------------------------------
	public void upgrade()
	{
		upgradeCount++;

		if ( slots.contains( EquipmentSlot.WEAPON, true ) )
		{
			// only upgrade attack
			int currentAtk = constantEvent.getStatistic( Statistic.emptyMap, Statistic.ATTACK );
			int newAtk = currentAtk + (int)(currentAtk * 0.1f);
			constantEvent.putStatistic( Statistic.ATTACK, ""+newAtk );
		}
		else
		{
			for (Statistic stat : Statistic.values())
			{
				if (constantEvent.equations.containsKey( stat ))
				{
					int currentVal = constantEvent.getStatistic( Statistic.emptyMap, stat );
					int newVal = currentVal + (int)(currentVal * 0.1f);
					constantEvent.putStatistic( stat, ""+newVal );
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	public static Item load( String name )
	{
		Item item = new Item();

		item.internalLoad( name );

		return item;
	}

	// ----------------------------------------------------------------------
	public static Item load( Element xml )
	{
		Item item = null;

		if ( xml.getChildByName( "Recipe" ) != null )
		{
			String recipe = Global.capitalizeString( xml.getChildByName( "Recipe" ).getText() );
			String material = xml.get( "Material" );
			int quality = xml.getInt( "Quality" );

			Item materialItem = TreasureGenerator.getMaterial( material, quality, MathUtils.random );

			item = Recipe.createRecipe( recipe, materialItem );
			item.getIcon().colour.mul( materialItem.getIcon().colour );

			Element prefixElement = xml.getChildByName( "Prefix" );
			if ( prefixElement != null )
			{
				String[] prefixes = prefixElement.getText().split( "," );

				for (String prefix : prefixes)
				{
					Recipe.applyModifer( item, prefix, materialItem.quality, true );
				}
			}

			Element suffixElement = xml.getChildByName( "Suffix" );
			if ( suffixElement != null )
			{
				String[] suffixes = suffixElement.getText().split( "," );

				for (String suffix : suffixes)
				{
					Recipe.applyModifer( item, suffix, materialItem.quality, false );
				}
			}
		}
		else
		{
			item = new Item();
			item.internalLoad( xml );
		}

		return item;
	}

	// ----------------------------------------------------------------------
	public boolean shouldDrop()
	{
		if ( dropChanceEqn == null ) { return true; }

		ExpressionBuilder expB = EquationHelper.createEquationBuilder( dropChanceEqn );

		Expression exp = EquationHelper.tryBuild( expB );
		if ( exp == null ) { return false; }

		double conditionVal = exp.evaluate();

		return conditionVal == 1;
	}

	// ----------------------------------------------------------------------
	public Table createTable( Skin skin, GameEntity entity )
	{
		if ( ability != null ) { return ability.current.current.createTable( skin, entity ); }

		Inventory inventory = entity.getInventory();

		if ( slots.contains( EquipmentSlot.WEAPON, true ) )
		{
			Item other = inventory.getEquip( EquipmentSlot.WEAPON );
			return createWeaponTable( other, entity, skin );
		}
		else if ( slots.size > 0 )
		{
			Item other = inventory.getEquip( slots.get( 0 ) );
			return createArmourTable( other, entity, skin );
		}

		Table table = new Table();

		table.add( new Label( getName(), skin, "title" ) ).left();
		if ( count > 1 )
		{
			table.add( new Label( "x" + count, skin ) ).left().padLeft( 20 );
		}

		table.row();

		Label descLabel = new Label( description, skin );
		descLabel.setWrap( true );
		table.add( descLabel ).expand().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
		table.row();

		return table;
	}

	// ----------------------------------------------------------------------
	private Table createArmourTable( Item other, GameEntity entity, Skin skin )
	{
		Table table = new Table();

		Table titleRow = new Table();

		titleRow.add( new Label( name, skin, "title" ) ).expandX().left();

		if ( type != null && type.length() > 0 )
		{
			Label label = new Label( type, skin );
			label.setFontScale( 0.7f );
			titleRow.add( label ).expandX().right();
		}

		table.add( titleRow ).expandX().fillX();
		table.row();

		Label descLabel = new Label( description, skin );
		descLabel.setWrap( true );
		table.add( descLabel ).expand().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
		table.row();

		table.add( new Seperator( skin, false ) ).expandX().fillX();
		table.row();

		for (Statistic stat : Statistic.values())
		{
			int val = getStatistic( entity.getVariableMap(), stat );
			int otherVal = other != null ? other.getStatistic( entity.getVariableMap(), stat ) : 0;

			if (val > 0 || val != otherVal)
			{
				String value = ""+val;

				if (val < otherVal)
				{
					value = value + "[RED] " + (val - otherVal) + "[]";
				}
				else if (val > otherVal)
				{
					value = value + "[GREEN] +" + (val - otherVal) + "[]";
				}

				Label statLabel = new Label( Global.capitalizeString( stat.toString() ) + ": " + value, skin );
				table.add( statLabel ).expandX().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
				table.row();
			}
		}

		Array<String> lines = toString( entity.getVariableMap(), true );
		for (String line : lines)
		{
			if (line.equals( "---" ))
			{
				table.add( new Seperator( skin, false ) ).expandX().fillX();
			}
			else
			{
				Label lineLabel = new Label( line, skin );
				lineLabel.setWrap( true );
				table.add( lineLabel ).expandX().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
				table.row();
			}

			table.row();
		}

		return table;
	}

	// ----------------------------------------------------------------------
	private Table createWeaponTable( Item other, GameEntity entity, Skin skin )
	{
		Table table = new Table();

		Table titleRow = new Table();

		titleRow.add( new Label( name, skin, "title" ) ).expandX().left();

		if ( type != null && type.length() > 0 )
		{
			Label label = new Label( type, skin );
			label.setFontScale( 0.7f );
			titleRow.add( label ).expandX().right();
		}

		table.add( titleRow ).expandX().fillX();
		table.row();

		Label descLabel = new Label( description, skin );
		descLabel.setWrap( true );
		table.add( descLabel ).expand().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
		table.row();

		table.add( new Seperator( skin, false ) ).expandX().fillX();
		table.row();

		int oldDam = other != null ? Global.calculateScaledAttack( Statistic.statsBlockToVariableBlock( other.getStatistics( entity.getVariableMap() ) ), entity.getVariableMap() ) : 0;
		int newDam = Global.calculateScaledAttack( Statistic.statsBlockToVariableBlock( getStatistics( entity.getVariableMap() ) ), entity.getVariableMap() );

		String damText = "Damage: " + newDam;
		if ( newDam != oldDam )
		{
			int diff = newDam - oldDam;

			if ( diff > 0 )
			{
				damText += "[GREEN] +" + diff;
			}
			else
			{
				damText += "[RED] " + diff;
			}
		}

		table.add( new Label( damText, skin ) ).expandX().left();
		table.row();

		table.add( new Seperator( skin, false ) ).expandX().fillX();
		table.row();

		table.add( new Label( "Scales with:", skin ) ).expandX().left();
		table.row();

		for (Statistic stat : Statistic.values())
		{
			int val = getStatistic( entity.getVariableMap(), stat );
			int otherVal = other != null ? other.getStatistic( entity.getVariableMap(), stat ) : 0;

			if ( stat == Statistic.ATTACK || stat == Statistic.DEFENSE )
			{
				continue;
			}

			if (val > 0 || val != otherVal)
			{
				String scale = val > 0 ? Global.ScaleLevel.values()[ val - 1 ].toString() : "--";

				if (val < otherVal)
				{
					scale = "[RED] " + scale + "[]";
				}
				else if (val > otherVal)
				{
					scale = "[GREEN] " + scale + "[]";
				}

				Label statLabel = new Label( Global.capitalizeString( stat.toString() ) + ": " + scale, skin );
				table.add( statLabel ).expandX().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
				table.row();
			}
		}

		Array<String> lines = toString( entity.getVariableMap(), true );
		for (String line : lines)
		{
			if (line.equals( "---" ))
			{
				table.add( new Seperator( skin, false ) ).expandX().fillX();
			}
			else
			{
				Label lineLabel = new Label( line, skin );
				lineLabel.setWrap( true );
				table.add( lineLabel ).expandX().left().width( com.badlogic.gdx.scenes.scene2d.ui.Value.percentWidth( 1, table ) );
				table.row();
			}

			table.row();
		}

		return table;
	}

	// ----------------------------------------------------------------------
	@Override
	public String getName()
	{
		if ( ability != null ) { return ability.current.current.getName(); }

		return name;
	}

	// ----------------------------------------------------------------------
	@Override
	public String getDescription()
	{
		if ( ability != null ) { return ability.current.current.getDescription(); }

		return description;
	}

	// ----------------------------------------------------------------------
	@Override
	public Sprite getIcon()
	{
		if ( icon != null ) { return icon; }
		if ( ability != null) { return ability.current.current.getIcon(); }

		if ( icon == null )
		{
			icon = AssetManager.loadSprite( "white" );
		}
		return icon;
	}

	// ----------------------------------------------------------------------
	private void internalLoad( String name )
	{
		XmlReader xml = new XmlReader();
		Element xmlElement = null;

		try
		{
			xmlElement = xml.parse( Gdx.files.internal( "Items/" + name + ".xml" ) );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		internalLoad( xmlElement );
	}

	// ----------------------------------------------------------------------
	private void internalLoad( Element xmlElement )
	{
		String extendsElement = xmlElement.getAttribute( "Extends", null );
		if ( extendsElement != null )
		{
			internalLoad( extendsElement );
		}

		name = xmlElement.get( "Name", name );
		description = xmlElement.get( "Description", description );
		canStack = xmlElement.getBoolean( "CanStack", canStack );

		String countEqn = xmlElement.get( "Count", "" + count );
		count = EquationHelper.evaluate( countEqn, Statistic.emptyMap );

		Element iconElement = xmlElement.getChildByName( "Icon" );
		if ( iconElement != null )
		{
			icon = AssetManager.loadSprite( iconElement );
		}

		Element hitElement = xmlElement.getChildByName( "HitEffect" );
		if ( hitElement != null )
		{
			hitEffect = AssetManager.loadSprite( hitElement );
		}

		Element eventsElement = xmlElement.getChildByName( "Events" );
		if ( eventsElement != null )
		{
			super.parse( eventsElement );
		}

		Element lightElement = xmlElement.getChildByName( "Light" );
		if ( lightElement != null )
		{
			light = Light.load( lightElement );
		}

		String slotsElement = xmlElement.get( "Slot", null );
		if ( slotsElement != null )
		{
			String[] split = slotsElement.split( "," );
			for ( String s : split )
			{
				slots.add( EquipmentSlot.valueOf( s.toUpperCase() ) );
			}
		}
		category = xmlElement.get( "Category", null ) != null ? ItemCategory.valueOf( xmlElement.get( "Category" ).toUpperCase() ) : category;
		type = xmlElement.get( "Type", type ).toLowerCase();
		quality = xmlElement.getInt( "Quality", quality );

		// Load the wep def
		if (slots.contains( EquipmentSlot.WEAPON, true ) && type != null)
		{
			wepDef = WeaponDefinition.load( type );
		}

		Element abilityElement = xmlElement.getChildByName( "Ability" );
		if ( abilityElement != null )
		{
			ability = new AbilityTree(abilityElement.getText());
		}

		// Preload sprites
		if ( type != null )
		{
			getWeaponHitEffect();
			getIcon();
		}
	}

	// ----------------------------------------------------------------------
	public EquipmentSlot getMainSlot()
	{
		return slots.size > 0 ? slots.get( 0 ) : null;
	}

	// ----------------------------------------------------------------------
	public Sprite getWeaponHitEffect()
	{
		if ( hitEffect != null ) { return hitEffect; }

		if ( wepDef != null && wepDef.hitSprite != null )
		{
			return wepDef.hitSprite;
		}

		return AssetManager.loadSprite( "EffectSprites/Strike/Strike", 0.1f, "Hit" );
	}

	// ----------------------------------------------------------------------
	public enum EquipmentSlot
	{
		// Weapons
		WEAPON,

		// Armour
		HEAD,
		BODY,
		LEGS
	}

	// ----------------------------------------------------------------------
	public enum ItemCategory
	{
		ARMOUR,
		WEAPON,
		JEWELRY,
		TREASURE,
		MATERIAL,
		MISC,

		ALL
	}

	// ----------------------------------------------------------------------
	public static class WeaponDefinition
	{
		public enum HitType
		{
			ALL,
			CLOSEST,
			RANDOM
		}

		public HitType hitType;
		public String hitData;
		public Sprite hitSprite;

		public Array<Point> hitPoints = new Array<Point>(  );

		public static WeaponDefinition load( String name )
		{
			WeaponDefinition wepDef = new WeaponDefinition();

			name = Global.capitalizeString( name );

			XmlReader reader = new XmlReader();
			XmlReader.Element xml = null;

			try
			{
				xml = reader.parse( Gdx.files.internal( "Items/Weapons/" + name + ".xml" ) );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

			Element spriteElement = xml.getChildByName( "HitSprite" );
			if ( spriteElement != null )
			{
				wepDef.hitSprite = AssetManager.loadSprite( spriteElement );
			}

			String[] hitTypeData = xml.get( "HitType" ).split( "[\\(\\)]" );
			wepDef.hitType = HitType.valueOf( hitTypeData[0].toUpperCase() );
			wepDef.hitData = hitTypeData.length > 1 ? hitTypeData[1] : null;

			Element hitPatternElement = xml.getChildByName( "HitPattern" );

			char[][] hitGrid = new char[hitPatternElement.getChildCount()][];
			Point centralPoint = new Point();

			for (int y = 0; y < hitPatternElement.getChildCount(); y++)
			{
				Element lineElement = hitPatternElement.getChild( y );
				String text = lineElement.getText();

				hitGrid[ y ] = text.toCharArray();

				for (int x = 0; x < hitGrid[ y ].length; x++)
				{
					if (hitGrid[ y ][ x ] == 'x')
					{
						centralPoint.x = x;
						centralPoint.y = y;
					}
				}
			}

			for (int y = 0; y < hitGrid.length; y++)
			{
				for (int x = 0; x < hitGrid[0].length; x++)
				{
					if (hitGrid[y][x] == '#')
					{
						int dx = x - centralPoint.x;
						int dy = centralPoint.y - y;

						wepDef.hitPoints.add( new Point(dx, dy) );
					}
				}
			}

			return wepDef;
		}
	}
}
