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

package com.analog.lyric.dimple.solvers.gibbs.samplers.conjugate;

import static com.analog.lyric.dimple.environment.DimpleEnvironment.*;
import static java.util.Objects.*;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.data.IDatum;
import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.factorfunctions.Dirichlet;
import com.analog.lyric.dimple.factorfunctions.ExchangeableDirichlet;
import com.analog.lyric.dimple.factorfunctions.core.IUnaryFactorFunction;
import com.analog.lyric.dimple.model.domains.RealDomain;
import com.analog.lyric.dimple.model.domains.RealJointDomain;
import com.analog.lyric.dimple.model.values.Value;
import com.analog.lyric.dimple.solvers.core.parameterizedMessages.DirichletParameters;
import com.analog.lyric.dimple.solvers.core.parameterizedMessages.IParameterizedMessage;
import com.analog.lyric.dimple.solvers.interfaces.ISolverEdgeState;
import com.analog.lyric.math.DimpleRandom;


public class DirichletSampler implements IRealJointConjugateSampler
{
	private DirichletParameters _parameters = new DirichletParameters();
	private int _dimension = -1;
	
	@Override
	public final double[] nextSample(ISolverEdgeState[] edges, List<? extends IDatum> inputs)
	{
		aggregateParameters(_parameters, edges, inputs);
		return nextSample(_parameters);
	}
	
	@Override
	public final void aggregateParameters(IParameterizedMessage aggregateParameters, ISolverEdgeState[] edges,
		List<? extends IDatum> inputs)
	{
		if (_dimension < 0)	// Just do this once
			setDimension(edges, inputs);
		final int dimension = _dimension;

		DirichletParameters parameters = (DirichletParameters)aggregateParameters;
		if (parameters.getSize() != dimension)
			parameters.setSize(dimension);
		parameters.setNull();
		
		for (IDatum input : inputs)
		{
			DirichletParameters inputParameters = null;
			
			if (input instanceof DirichletParameters)
				inputParameters = (DirichletParameters)input;
			else if (input instanceof Dirichlet)
				inputParameters = ((Dirichlet)input).getParameterizedMessage();
			else if (input instanceof ExchangeableDirichlet)
				inputParameters = ((ExchangeableDirichlet)input).getParameterizedMessage();
			else
				continue; // This should not be possible
			
			if (inputParameters.getSize() != dimension)
				throw new DimpleException("All inputs to Dirichlet sampler must have the same number of dimensions");
			
			parameters.addFrom(inputParameters);
		}
		
		final int numEdges = edges.length;
		for (int i = 0; i < numEdges; i++)
		{
			// The message from each neighboring factor is an array with elements (alpha, beta)
			DirichletParameters message = requireNonNull((DirichletParameters)edges[i].getFactorToVarMsg());
			int messageSize = message.getSize();
			if (messageSize == 0)	// Uninitialized message, which implies uninformative
			{
				message.setSize(dimension);
				message.setNull();
				continue;
			}
			else if (messageSize != dimension)
				throw new DimpleException("All inputs to Dirichlet sampler must have the same number of dimensions");
			
			parameters.addFrom(message);
		}
	}
	
	public final double[] nextSample(DirichletParameters parameters)
	{
		final DimpleRandom rand = activeRandom();
		
		// Sample from a series of Gamma distributions, then normalize to sum to 1
		int dimension = parameters.getSize();
		double[] sample = new double[dimension];
		double sum = 0;
		int numZeros = 0;
		for (int i = 0; i < dimension; i++)
		{
			double nextSample = rand.nextGamma(parameters.getAlphaMinusOne(i) + 1, 1);
			sample[i] = nextSample;
			sum += nextSample;
			if (nextSample == 0)
				numZeros++;
		}
		if (numZeros == 0)
		{
			for (int i = 0; i < dimension; i++)
				sample[i] /= sum;
		}
		else if (numZeros < dimension)
		{
			// Corner case where some, but not all, of the samples are zero
			// Add a little to the zero sample values and adjust the others accordingly
			double zeroAdjustment = Double.MIN_VALUE * numZeros / (dimension - numZeros);
			for (int i = 0; i < dimension; i++)
			{
				if (sample[i] == 0)
					sample[i] = Double.MIN_VALUE;
				else
					sample[i] = (sample[i] / sum) - zeroAdjustment;
			}
		}
		else
		{
			// Corner case where all samples were zero
			// Choose one sample value at random, make that (nearly) one, and the others (nearly) zero
			int randomChoice = rand.nextInt(dimension);
			for (int i = 0; i < dimension; i++)
				if (i != randomChoice)
					sample[i] = Double.MIN_VALUE;
			sample[randomChoice] = 1 - Double.MIN_VALUE * (dimension - 1);
		}
		return sample;
	}
	
	@Override
	public IParameterizedMessage createParameterMessage()
	{
		return new DirichletParameters();
	}
	
	@SuppressWarnings("null")
	private void setDimension(ISolverEdgeState[] sedges, List<? extends IDatum> inputs)
	{
		int numEdges = sedges.length;
		int dimension = 0;
		if (numEdges > 0)
			dimension = ((DirichletParameters)sedges[0].getFactorToVarMsg()).getSize();
		else if (inputs.size() > 0)
		{
			IDatum input = inputs.get(0);
			if (input instanceof DirichletParameters)
				dimension = ((DirichletParameters)input).getSize();
			else if (input instanceof Value)
				dimension = ((Value)input).getDoubleArray().length;
			else if (input instanceof Dirichlet)
				dimension = ((Dirichlet)input).getDimension();
			else if (input instanceof ExchangeableDirichlet)
				dimension = ((ExchangeableDirichlet)input).getDimension();
		}
		
		if (dimension == 0)
		{
			throw new DimpleException("Cannot determine Dirichlet dimension from edges or inputs.");
		}
		
		_parameters.setSize(dimension);
		_dimension = dimension;
	}

	
	// A static factory that creates a sampler of this type
	public static final IRealJointConjugateSamplerFactory factory = new IRealJointConjugateSamplerFactory()
	{
		@Override
		public IRealJointConjugateSampler create() {return new DirichletSampler();}
		
		@Override
		public boolean isCompatible(@Nullable IUnaryFactorFunction factorFunction)
		{
			if (factorFunction == null)
				return true;
			else if (factorFunction instanceof Dirichlet || factorFunction instanceof DirichletParameters)
				return true;
			else if (factorFunction instanceof ExchangeableDirichlet)
				return true;
			else
				return false;
		}
		
		@Override
		public boolean isCompatible(RealJointDomain domain)
		{
			for (RealDomain d : domain.getRealDomains())
			{
				if (d.getLowerBound() > 0 || d.getUpperBound() < 1)
					return false;
			}
			return true;
		}
	};
}
