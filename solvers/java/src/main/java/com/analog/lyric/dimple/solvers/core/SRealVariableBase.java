/*******************************************************************************
*   Copyright 2014 Analog Devices, Inc.
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
********************************************************************************/

package com.analog.lyric.dimple.solvers.core;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.model.domains.RealDomain;
import com.analog.lyric.dimple.model.variables.Real;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactorGraph;

public abstract class SRealVariableBase extends SVariableBase<Real>
{
	/*-----------
	 * Constants
	 */
	
	/**
	 * Bits in {@link #_flags} reserved by this class and its superclasses.
	 */
	@SuppressWarnings("hiding")
	protected final static int RESERVED_FLAGS = 0xFFFF0000;
	
	/*-------
	 * State
	 */
	
    protected double _guessValue = Double.NaN;
    protected boolean _guessWasSet = false;

    /*---------------
     * Construction
     */
    
	protected SRealVariableBase(Real var, ISolverFactorGraph parent)
	{
		super(var, parent);
	}

	@Override
	public void initialize()
	{
		super.initialize();
		setGuess(null);
	}
	
	/*-------------------------
	 * ISolverVariable methods
	 */
	
	@Override
	public RealDomain getDomain()
	{
		return getModelObject().getDomain();
	}
	
	@Override
	public boolean guessWasSet()
	{
		return _guessWasSet;
	}
	
	@Override
	public Object getGuess()
	{
		if (_guessWasSet)
			return Double.valueOf(_guessValue);
		
		Object value = getKnownValueObject();
		if (value != null)
		{
			// If there's a fixed value set, use that
			// Note: we could also look at value from default conditioning layer, but
			// since we intend to deprecate the guess mechanism, it is probably not worth it.
			return value;
		}
		
		return getValue();
	}
	
	@Override
	public void setGuess(@Nullable Object guess)
	{
		if (guess == null)
		{
			_guessWasSet = false;
			_guessValue = Double.NaN;
		}
		else
		{
			// Convert the guess to a number
			if (guess instanceof Double)
				_guessValue = (Double)guess;
			else if (guess instanceof Integer)
				_guessValue = (Integer)guess;
			else
				throw new DimpleException("Guess is not a value type (must be Double or Integer)");

			// Make sure the number is within the domain of the variable
			if (!_model.getDomain().inDomain(_guessValue))
				throw new DimpleException("Guess is not within the domain of the variable");

			_guessWasSet = true;
		}
	}
	
}
