package com.github.lindenb.xmake;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;


public class DepFile
	{
	private final static Logger LOG = Logger.getLogger(XMake.class.getName()); 

	Rule rule=null;
	private String name;
	Status status = Status.NIL;
	Set<DepFile> prerequisites = new HashSet<DepFile>();
	DepFile(String name)
		{
		this.name=name;
		}
	
	public boolean hasDependency(DepFile f)
		{
		for(DepFile dp:this.prerequisites)
			{
			if(dp.equals(f)) return true;
			if(dp.hasDependency(f)) return true;
			}
		return false;
		}
	
	Status prepare()
		{
		if(status!=Status.NIL) return status;
		if( ! exists() )
			{
			status=Status.DIRTY;
			if( this.rule == null )
				{
				LOG.info("no rule to create "+name);
				status=Status.FATAL;
				return status;
				}
			
			}
		return status;
		}	
	
	public void setStatus(Status status) {
		this.status = status;
		}
		
	public File toFile()
		{
		return new File(this.name);
		}
	
	public boolean exists()
		{
		return toFile().exists();
		}
	
		
	@Override
	public int hashCode()
		{
		return this.name.hashCode();
		}
		
		
	@Override
	public boolean equals(Object o)
		{
		if(o == this) return true;
		if(o ==null || !(o instanceof DepFile)) return false;
		return this.name.equals( DepFile.class.cast(o).name );
		}
	
	@Override
	public String toString()
		{
		return this.name;
		}
	}
