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

import java.util.List;

import com.analog.lyric.dimple.data.IDatum;
import com.analog.lyric.dimple.solvers.interfaces.ISolverEdgeState;

public interface IRealJointConjugateSampler extends IConjugateSampler
{
	/**
	 * Note: previous version of this method had Ports instead of edges.
	 * @since 0.08
	 */
	public double[] nextSample(ISolverEdgeState[] edges, List<? extends IDatum> inuts);
}
