package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
public enum Priority {
	NEXT(4), HIGH(3), NORMAL(2), LOW(1);
	final int weight;
	
	Priority(int weight) { 
		this.weight = weight; 
	}
	
	public int getWeight() {
		return weight; 
	}
}
