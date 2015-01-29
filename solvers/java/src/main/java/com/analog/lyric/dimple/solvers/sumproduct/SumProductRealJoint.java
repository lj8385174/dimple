/*******************************************************************************
*   Copyright 2012 Analog Devices, Inc.
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

package com.analog.lyric.dimple.solvers.sumproduct;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.collect.ArrayUtil;
import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.factorfunctions.MultivariateNormal;
import com.analog.lyric.dimple.factorfunctions.Normal;
import com.analog.lyric.dimple.model.variables.RealJoint;
import com.analog.lyric.dimple.solvers.core.SMultivariateNormalEdge;
import com.analog.lyric.dimple.solvers.core.SRealJointVariableBase;
import com.analog.lyric.dimple.solvers.core.parameterizedMessages.MultivariateNormalParameters;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactor;
import com.analog.lyric.dimple.solvers.interfaces.ISolverNode;

/**
 * Solver variable for RealJoint variables under Sum-Product solver.
 * 
 * @since 0.07
 */
public class SumProductRealJoint extends SRealJointVariableBase
{

	private int _numVars;
	private @Nullable MultivariateNormalParameters _input;

	public SumProductRealJoint(RealJoint var)
	{
		super(var);
		
		_numVars = _model.getDomain().getNumVars();
	}
	
	@Override
	public void setInputOrFixedValue(@Nullable Object input, @Nullable Object fixedValue)
	{
		if (fixedValue != null)
			_input = createFixedValueMessage((double[])fixedValue);
		else if (input == null)
			_input = null;
		else
		{
    		if (input instanceof MultivariateNormal)	// Input is a MultivariateNormal factor function with fixed parameters
    		{
    			MultivariateNormal multivariateNormalInput = (MultivariateNormal)input;
    			if (!multivariateNormalInput.hasConstantParameters())
    				throw new DimpleException("MultivariateNormal factor function used as Input must have constant parameters");
    			_input = multivariateNormalInput.getParameters();
    		}
    		else if (input instanceof Normal[])			// Input is array of univariate Normal factor functions with fixed parameters
    		{
    			Normal[] inputArray = (Normal[])input;
    			if (inputArray.length != _numVars)
    				throw new DimpleException("Number of Inputs must equal the variable dimension");
    			double[] mean = new double[_numVars];
    			double[][] covariance = new double[_numVars][_numVars];
    			for (int i = 0; i < _numVars; i++)
    			{
    				mean[i] = inputArray[i].getMean();
    				covariance[i][i] = inputArray[i].getVariance();		// Diagonal covariance matrix
    			}
    			_input = new MultivariateNormalParameters(mean, covariance);
    		}
    		else if (input instanceof MultivariateNormalParameters)		// Input is a MultivariateNormalParameters object
    		{
    			_input = (MultivariateNormalParameters)input;
    		}
    		else
    			throw new DimpleException("Invalid input type");

		}
	}
	

	@Override
	public Object getBelief()
	{
		MultivariateNormalParameters m = new MultivariateNormalParameters();
		doUpdate(m,-1);
		return m;
	}
	
	@Override
	public Object getValue()
	{
		MultivariateNormalParameters m = (MultivariateNormalParameters)getBelief();
		return m.getMean();
	}
	
	@Override
	public double getScore()
	{
		final MultivariateNormalParameters input = _input;
		if (input == null)
			return 0;
		else
			return (new MultivariateNormal(input)).evalEnergy(getGuess());
	}
	

	@Override
	protected void doUpdateEdge(int outPortNum)
	{
		doUpdate(getEdge(outPortNum).varToFactorMsg, outPortNum);
	}

	private void doUpdate(MultivariateNormalParameters outMsg, int outPortNum)
	{
		final MultivariateNormalParameters input = _input;
		
    	// If fixed value, just return the input, which has been set to a zero-variance message
		if (_model.hasFixedValue())
		{
			outMsg.set(Objects.requireNonNull(_input));
			return;
		}

		double[] vector;
		double[][] matrix;
		if (input == null)
		{
			vector = new double[_numVars];
			matrix = new double[_numVars][_numVars];
		}
		else
		{
			vector = input.getInformationVector();
			matrix = input.getInformationMatrix();
		}
		
		for (int i = 0, n = getSiblingCount(); i < n; i++ )
		{
			if (i != outPortNum)
			{
				final MultivariateNormalParameters inMsg = getEdge(i).factorToVarMsg;
				
				double [] inMsgVector = inMsg.getInformationVector();
				
				for (int j = 0; j < vector.length; j++)
					vector[j] += inMsgVector[j];
				
				double [][] inMsgMatrix = inMsg.getInformationMatrix();
				
				for (int j = 0; j < inMsgMatrix.length; j++)
					for (int k = 0; k < inMsgMatrix[j].length; k++)
						matrix[j][k] += inMsgMatrix[j][k];
			}
		}
		
		outMsg.setInformation(vector, matrix);
	}

	@Override
	public Object [] createMessages(ISolverFactor factor)
	{
		return ArrayUtil.EMPTY_OBJECT_ARRAY;
	}

	public MultivariateNormalParameters createDefaultMessage()
	{
		MultivariateNormalParameters mm = new MultivariateNormalParameters();
		return (MultivariateNormalParameters)resetInputMessage(mm);
	}

	@Override
	public Object resetInputMessage(Object message)
	{
		double[] mean = new double[_numVars];
		double[][] covariance = new double[_numVars][];
		
		
		for (int i = 0; i < covariance.length; i++)
		{
			covariance[i] = new double[_numVars];
			covariance[i][i] = Double.POSITIVE_INFINITY;
		}
		((MultivariateNormalParameters)message).setMeanAndCovariance(mean, covariance);
		return message;
	}

	@Override
	public void resetEdgeMessages( int i )
	{
		getEdge(i).reset();
	}
	
	@Override
	public void moveMessages(ISolverNode other, int portNum, int otherPort)
	{
		SumProductRealJoint s = (SumProductRealJoint)other;
		final SMultivariateNormalEdge thisEdge = getEdge(portNum);
		final SMultivariateNormalEdge otherEdge = s.getEdge(otherPort);

		thisEdge.factorToVarMsg.set(otherEdge.factorToVarMsg);
		thisEdge.varToFactorMsg.set(otherEdge.varToFactorMsg);
		otherEdge.reset();
	}
	
	@Override
	public Object getInputMsg(int portIndex)
	{
		return getEdge(portIndex).factorToVarMsg;
	}

	@Override
	public Object getOutputMsg(int portIndex)
	{
		return getEdge(portIndex).varToFactorMsg;
	}
	
	@Override
	public void setInputMsg(int portIndex, Object obj)
	{
		getEdge(portIndex).factorToVarMsg.set((MultivariateNormalParameters)obj);
	}


	public MultivariateNormalParameters createFixedValueMessage(double[] fixedValue)
	{
		double[][] covariance = new double[_numVars][_numVars];
		for (int i = 0; i < _numVars; i++)
			Arrays.fill(covariance[i], 0);
		MultivariateNormalParameters message = new MultivariateNormalParameters(fixedValue, covariance);
		return message;
	}

	/*-----------------------
	 * SVariableBase methods
	 */
	
	@Override
	protected MultivariateNormalParameters cloneMessage(int edge)
	{
		return getEdge(edge).varToFactorMsg.clone();
	}
	
	@Override
	protected boolean supportsMessageEvents()
	{
		return true;
	}
	
	@SuppressWarnings("null")
	@Override
	protected SMultivariateNormalEdge getEdge(int siblingIndex)
	{
		return (SMultivariateNormalEdge)super.getEdge(siblingIndex);
	}
}
