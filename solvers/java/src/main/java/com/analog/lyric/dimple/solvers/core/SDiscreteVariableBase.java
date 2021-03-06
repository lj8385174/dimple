/*******************************************************************************
*   Copyright 2013 Analog Devices, Inc.
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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.data.IDatum;
import com.analog.lyric.dimple.environment.DimpleEnvironment;
import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.model.domains.DiscreteDomain;
import com.analog.lyric.dimple.model.values.Value;
import com.analog.lyric.dimple.model.variables.Discrete;
import com.analog.lyric.dimple.solvers.core.parameterizedMessages.DiscreteEnergyMessage;
import com.analog.lyric.dimple.solvers.core.parameterizedMessages.DiscreteMessage;
import com.analog.lyric.dimple.solvers.interfaces.IDiscreteSolverVariable;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactorGraph;
import com.analog.lyric.util.misc.Internal;

public abstract class SDiscreteVariableBase extends SVariableBase<Discrete> implements IDiscreteSolverVariable
{
	protected int _guessIndex = -1;
	protected boolean _guessWasSet = false;

    
	protected SDiscreteVariableBase(Discrete var, ISolverFactorGraph parent)
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
	public abstract double[] getBelief();
	
	@Override
	public DiscreteDomain getDomain()
	{
		return getModelObject().getDomain();
	}
	
	@Override
	public Object getValue()
	{
		int index = getValueIndex();
		return _model.getDiscreteDomain().getElement(index);
	}
	
	@Override
	public int getValueIndex()
	{
		PriorAndCondition known = getPriorAndCondition();
		final Value value = known.value();
		known = known.release();
		
		if (value != null)
		{
			return value.getIndex();
		}
					
		double[] belief = getBelief();
		int numValues = belief.length;
		double maxBelief = Double.NEGATIVE_INFINITY;
		int maxBeliefIndex = -1;
		for (int i = 0; i < numValues; i++)
		{
			double b = belief[i];
			if (b > maxBelief)
			{
				maxBelief = b;
				maxBeliefIndex = i;
			}
		}
		return maxBeliefIndex;
	}

	@Override
	public boolean guessWasSet()
	{
		return _guessWasSet;
	}
	
	@Override
	public Object getGuess()
	{
		int index = getGuessIndex();
		return _model.getDomain().getElement(index);
	}
	
	@Override
	public void setGuess(@Nullable Object guess)
	{
		if (guess == null)
		{
			_guessWasSet = false;
			_guessIndex = -1;
		}
		else
		{
			DiscreteDomain domain = _model.getDomain();
			setGuessIndex(domain.getIndexOrThrow(guess));
		}
	}
	
	@Override
	public int getGuessIndex()
	{
		int index = 0;
		if (_guessWasSet)
			index = _guessIndex;
		else
			index = getValueIndex();
		
		return index;
	}
	

	@Override
	public void setGuessIndex(int index)
	{
		if (index < 0 || index >= _model.getDomain().size())
			throw new DimpleException("illegal index");
		
		_guessWasSet = true;
		_guessIndex = index;
	}
	
	/*------------------
	 * Internal methods
	 */
	
	/**
	 * Gets prior from model as a {@link DiscreteMessage}
	 * <p>
	 * Returns null if prior is not a {@link DiscreteMessage}. Logs error if prior
	 * is not a {@link Value} or {@link DiscreteMessage}.
	 * @since 0.08
	 * @category internal
	 */
	@Deprecated
	@Internal
	public @Nullable DiscreteMessage getPrior()
	{
		IDatum prior = _model.getPrior();
		
		if (prior instanceof DiscreteMessage || prior == null)
			return (DiscreteMessage)prior;

		if (!(prior instanceof Value))
		{
			DimpleEnvironment.logError("Prior %s ignored on %s: type not supported", prior, _model);
		}
		
		return null;
	}
	
	/**
	 * Converts data for variable to a single {@link DiscreteEnergyMessage}
	 * <p>
	 * Simply calls {@link DiscreteEnergyMessage#convertFrom(DiscreteDomain, List)} with
	 * this variable's {@linkplain #getDomain domain}.
	 * <p>
	 * @param data is any list of data compatible with this variable, but is typically expected to be
	 * a {@link PriorAndCondition} instance.
	 * @since 0.08
	 * @see #getPriorAndCondition()
	 */
	protected @Nullable DiscreteEnergyMessage toEnergyMessage(List<IDatum> data)
	{
		return DiscreteEnergyMessage.convertFrom(getDomain(),data);
	}
	
	protected @Nullable DiscreteEnergyMessage knownEnergyMessage()
	{
		final PriorAndCondition known = getPriorAndCondition();
		final DiscreteEnergyMessage msg = toEnergyMessage(known);
		known.release();
		return msg;
	}
}
