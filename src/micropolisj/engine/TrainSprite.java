// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;

import static micropolisj.engine.TileConstants.*;

/**
 * Implements the commuter train.
 * The commuter train appears if the city has a certain amount of
 * railroad track. It wanders around the city's available track
 * randomly.
 */
public class TrainSprite extends Sprite
{
	static int [] Cx = { 0, 16, 0, -16 };
	static int [] Cy = { -16, 0, 16, 0 };
	static int [] Dx = { 0, 4, 0, -4, 0 };
	static int [] Dy = { -4, 0, 4, 0, 0 };
	static int [] TrainPic2 = { 1, 2, 1, 2, 5 };
	static final int TRA_GROOVE_X = 8;
	static final int TRA_GROOVE_Y = 8;

	static final int FRAME_NORTHSOUTH = 1;
	static final int FRAME_EASTWEST = 2;
	static final int FRAME_NW_SE = 3;
	static final int FRAME_SW_NE = 4;
	static final int FRAME_UNDERWATER = 5;

	static final int DIR_NORTH = 0;
	static final int DIR_EAST = 1;
	static final int DIR_SOUTH = 2;
	static final int DIR_WEST = 3;
	static final int DIR_NONE = 4; //not moving

	int step;
	int stepdir;
	CityLocation loc;
	TrackStep [] track;

	public TrainSprite(Micropolis engine, int xpos, int ypos)
	{
		super(engine, SpriteKind.TRA);
		this.x = xpos * 16 + TRA_GROOVE_X;
		this.y = ypos * 16 + TRA_GROOVE_Y;
		this.offx = -16;
		this.offy = -16;
		this.dir = DIR_NONE;   //not moving

		this.step = 0;
		this.stepdir = 1;
		this.loc = new CityLocation(xpos, ypos);
		this.frame = 1;
	}

	@Override
	public void moveImpl()
	{
		if (this.track == null) {
			moveImplOld();
			return;
		}

		this.step += this.stepdir;
		if (stepdir > 0 && step >= track.length) {
			leaveNewTrack();
		}
		else if (stepdir < 0 && step < 0) {
			leaveNewTrack();
		}
		else {
			setNewTrackPos();
		}
	}

	void setNewTrackPos()
	{
		int offX;
		int offY;
		int aDir;

		if (step >= 0 && step < track.length) {
			aDir = track[step].dir;
			offX = track[step].offX;
			offY = track[step].offY;
		}
		else if (step < 0) {
			aDir = track[0].dir;
			offX = track[0].offX + (aDir == 0 ? 4 : aDir == 180 ? -4 : 0) * (step);
			offY = track[0].offY + (aDir == 90 ? -4 : aDir == 270 ? 4 : 0) * (step);
		}
		else {
			assert step >= track.length;
			int z = step+1 - track.length;
			aDir = track[track.length-1].dir;
			offX = track[track.length-1].offX + (aDir == 0 ? 4 : aDir == 180 ? -4 : 0) * z;
			offY = track[track.length-1].offY + (aDir == 90 ? -4 : aDir == 270 ? 4 : 0) * z;
		}

		this.x = loc.x*16 + TRA_GROOVE_X + offX;
		this.y = loc.y*16 + TRA_GROOVE_Y + offY;
		this.frame = getFrame(stepdir > 0 ? aDir : oppositeAngle(aDir));
	}

	int oppositeAngle(int dir)
	{
		return (dir+180) % 360;
	}

	void leaveNewTrack()
	{
		int baseX = loc.x * 16 + TRA_GROOVE_X;
		int baseY = loc.y * 16 + TRA_GROOVE_Y;

		if (this.x > baseX) {
			this.dir = DIR_EAST;
			this.track = null;
			this.loc = null;
			this.step = 2;
		}
		else if (this.y > baseY) {
			this.dir = DIR_SOUTH;
			this.track = null;
			this.loc = null;
			this.step = 2;
		}

		moveImplOld();
	}

	void moveImplOld()
	{	
		if (frame == 3 || frame == 4) {
			frame = TrainPic2[this.dir];
		}
		x += Dx[this.dir];
		y += Dy[this.dir];
		if (++this.step < 4) {
			// continue present course
			return;
		}

		// should be at the center of a cell, if not, correct it
		x = (x/16) * 16 + TRA_GROOVE_X;
		y = (y/16) * 16 + TRA_GROOVE_Y;
		this.step = 0;

		// pick new direction
		int d1 = city.PRNG.nextInt(4);
		for (int z = d1; z < d1 + 4; z++) {
			int d2 = z % 4;
			if (this.dir != DIR_NONE) { //impossible?
				if (d2 == (this.dir + 2) % 4)
					continue;
			}

			int c = getChar(this.x + Cx[d2], this.y + Cy[d2]);
			if (((c >= RAILBASE) && (c <= LASTRAIL)) || //track?
				(c == RAILVPOWERH) ||
				(c == RAILHPOWERV))
			{
				if ((this.dir != d2) && (this.dir != DIR_NONE)) {
					if (this.dir + d2 == 3)
						this.frame = FRAME_NW_SE;
					else
						this.frame = FRAME_SW_NE;
				}
				else {
					this.frame = TrainPic2[d2];
				}

				if ((c == RAILBASE) || (c == (RAILBASE+1))) {
					//underwater
					this.frame = FRAME_UNDERWATER;
				}
				this.dir = d2;
				return;
			}
			else if (canEnterNewTrack((this.x + Cx[d2])/16, (this.y + Cy[d2])/16, d2)) {
				enterNewTrack(
					(this.x + Cx[d2]) / 16,
					(this.y + Cy[d2]) / 16
					);
				return;
			}
		}
		if (this.dir == DIR_NONE) {
			// train has nowhere to go, so retire
			this.frame = 0;
			return;
		}
		// stop the train for a moment, before allowing traveling in reverse direction
		this.dir = DIR_NONE;
	}

	boolean canEnterNewTrack(int xpos, int ypos, int d2)
	{
		TileSpec spec = Tiles.get(city.getTile(xpos, ypos));
		if (spec.owner != null) {
			xpos -= spec.ownerOffsetX;
			ypos -= spec.ownerOffsetY;
			spec = spec.owner;
		}
		if (spec.getAttribute("track") == null) {
			return false;
		}

		int baseX = xpos * 16 + TRA_GROOVE_X;
		int baseY = ypos * 16 + TRA_GROOVE_Y;

		TrackStep [] atrack = parseTrackInfo(spec.getAttribute("track"));
		int xx = this.x + (d2 == DIR_EAST ? 8 : d2 == DIR_WEST ? -8 : 0) - baseX;
		int yy = this.y + (d2 == DIR_NORTH ? -8 : d2 == DIR_SOUTH ? 8 : 0) - baseY;

		if (atrack[0].offX == xx && atrack[0].offY == yy) {
			return true;
		}
		if (atrack[atrack.length-1].offX == xx && atrack[atrack.length-1].offY == yy) {
			return true;
		}
		return false;
	}

	void enterNewTrack(int xpos, int ypos)
	{
		TileSpec spec = Tiles.get(city.getTile(xpos, ypos));
		if (spec.owner != null) {
			xpos -= spec.ownerOffsetX;
			ypos -= spec.ownerOffsetY;
			spec = spec.owner;
		}
		this.loc = new CityLocation(xpos, ypos);
		this.track = parseTrackInfo(spec.getAttribute("track"));

		int startX = loc.x * 16 + TRA_GROOVE_X + track[0].offX;
		int startY = loc.y * 16 + TRA_GROOVE_Y + track[0].offY;
		int endX = loc.x * 16 + TRA_GROOVE_X + track[track.length-1].offX;
		int endY = loc.y * 16 + TRA_GROOVE_Y + track[track.length-1].offY;

		if (Math.abs(this.x-startX) + Math.abs(this.y-startY) <= Math.abs(this.x-endX) + Math.abs(this.y-endY)) {
			this.step = -2;
			this.stepdir = 1;
			setNewTrackPos();
		}
		else {
			this.step = track.length - 1 + 2;
			this.stepdir = -1;
			setNewTrackPos();
		}
	}

	static class TrackStep
	{
		int offX;
		int offY;
		int dir;
	}

	TrackStep [] parseTrackInfo(String trackInfo)
	{
		String [] parts = trackInfo.split(";");
		TrackStep [] rv = new TrackStep[parts.length];
		for (int i = 0; i < parts.length; i++) {
			rv[i] = new TrackStep();
			String [] aa = parts[i].split(",");
			rv[i].offX = Integer.parseInt(aa[0]);
			rv[i].offY = Integer.parseInt(aa[1]);
			rv[i].dir = Integer.parseInt(aa[2]);
		}
		return rv;
	}

	int getFrame(int dir)
	{
		return
			dir == 0   || dir == 180 ? FRAME_EASTWEST :
			dir == 45  || dir == 225 ? FRAME_SW_NE :
			dir == 90  || dir == 270 ? FRAME_NORTHSOUTH :
			dir == 135 || dir == 315 ? FRAME_NW_SE : 1;
	}
}
