%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%   Copyright 2012 Analog Devices, Inc.
%
%   Licensed under the Apache License, Version 2.0 (the "License");
%   you may not use this file except in compliance with the License.
%   You may obtain a copy of the License at
%
%       http://www.apache.org/licenses/LICENSE-2.0
%
%   Unless required by applicable law or agreed to in writing, software
%   distributed under the License is distributed on an "AS IS" BASIS,
%   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
%   See the License for the specific language governing permissions and
%   limitations under the License.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

classdef MatrixObject < handle
    properties (Access=public)
        VectorIndices;
        VectorObject;
    end
    methods
        function obj = MatrixObject(vectorObject,VectorIndices)
            obj.VectorIndices = VectorIndices;
            obj.VectorObject = vectorObject;
        end
        
        function varargout = subsref(obj,S)
            
            varargout = cell(1,max(1,nargout));
            switch S(1).type
                case '()'
                    %TODO: put in subroutine?
                    ind = obj.VectorIndices(S(1).subs{:});
                    newobj = obj.createObjectFromReorderedVectorIndices(ind);
                    
                    if (length(S) > 1)
                        [varargout{:}] = subsref(newobj,S(2:end));
                    else
                        varargout = {newobj};
                    end
                    
                case '.'
                    
                    %It would be nice if this could be dealt with more
                    %elegantly.
                    try
                        [varargout{:}] = builtin('subsref',obj,S);
                    catch e                        
                        if isequal(e.identifier,'MATLAB:unassignedOutputs')
                        elseif isequal(e.identifier,'MATLAB:maxlhs')
                            builtin('subsref',obj,S);
                        else
                            rethrow(e);
                        end
                    end
                case '{}'
                    error('brackets are not supported');
            end
        end
        
        function obj = subsasgn(obj,S,B)
            
            if numel(S) > 1
                
                %This is needed to differentiate between properties and
                %function calls.
                if isequal(S(2).type,'()')
                    firstArgs = S(1:2);                    
                    secondArgs = S(3:end);
                else
                    firstArgs = S(1);
                    secondArgs = S(2:end);
                end
                
                tmp = subsref(obj,firstArgs);
                a = subsasgn(tmp,secondArgs,B);
            else
                switch S(1).type
                    case '.'
                        a = builtin('subsasgn',obj,S,B);
                    case '()'
                        if ~ isa(B,'MatrixObject')
                            error('must assign matrix objects');
                        end
                        obj.verifyCanConcatenate({B});
                        VectorIndices = obj.VectorIndices(S(1).subs{:});
                        obj.VectorObject.replace(B.VectorObject,VectorIndices(:));
                    case '{}'
                        error('{} not supported');
                end
            end
        end
        
        function x = length(obj)
            x = length(obj.VectorIndices);
        end
        
        function x = size(obj,varargin)
            x = size(obj.VectorIndices,varargin{:});
        end
        
        %Cannot implement this as it messes up the builtin('subsref')
        %and builtin('subsasgn').  Would be nice to figure out a way to fix
        %this.
        %function x = numel(obj)
        %    x = numel(obj.VectorIndices);
        %end
        
        function x = end(obj,k,n)
            if n == 1
                x = numel(obj.VectorIndices);
            else
                x = size(obj.VectorIndices,k);
            end
        end
        
        function var = repmat(a,varargin)
            VectorIndices = a.VectorIndices;
            VectorIndices = repmat(VectorIndices,varargin{:});
            var = a.createObjectFromReorderedVectorIndices(VectorIndices);
        end
        
        
        function retval = isequal(a,b)
            if ~isa(b,'MatrixObject')
                retval = false;
            else
                retval = isequal(a.VectorIndices,b.VectorIndices) && ...
                    isequal(a.VectorObject.getIds(),b.VectorObject.getIds());
            end
        end
        
        function retval = eq(a,b)
            retval = a.isequal(b);
        end
        
        function x = ctranspose(a)
            x = a.transpose();
        end
        function x = transpose(a)
            x = a.createObject(a.VectorObject,a.VectorIndices');
        end
        
        function x = reshape(obj,varargin)
            VectorIndices = reshape(obj.VectorIndices,varargin{:});
            x = obj.createObject(obj.VectorObject,VectorIndices);
        end
        
        function x = fliplr(obj)
            VectorIndices = fliplr(obj.VectorIndices);
            x = obj.createObjectFromReorderedVectorIndices(VectorIndices);
        end
        
        function x = flipud(obj)
            VectorIndices = flipud(obj.VectorIndices);
            x = obj.createObjectFromReorderedVectorIndices(VectorIndices);
        end
        
        function x = horzcat(varargin)
            x = varargin{1}.docat(@horzcat,varargin{:});
        end
        
        function x = vertcat(varargin)
            x = varargin{1}.docat(@vertcat,varargin{:});
        end
        
    end
    
    methods(Access=private)
        
        %Private
        function x = createObjectFromReorderedVectorIndices(obj,VectorIndices)
            varids = reshape(VectorIndices,numel(VectorIndices),1);
            vectorObject = obj.VectorObject.getSlice(varids);
            VectorIndices = reshape(0:(numel(varids)-1),size(VectorIndices));
            x = obj.createObject(vectorObject,VectorIndices);
        end
        
        function x = docat(obj,catmethod,varargin)
            
            obj.verifyCanConcatenate(varargin(2:end));
            
            VectorIndices_all = [];
            vector_object_VectorIndices_all = [];
            
            vectorObjects = cell(size(varargin));
            
            
            for i = 1:length(varargin)
                VectorIndices = varargin{i}.VectorIndices;
                vectorObjects{i} = varargin{i}.VectorObject;
                vector_object_VectorIndices = ones(size(VectorIndices))*i-1;
                VectorIndices_all = catmethod(VectorIndices_all,VectorIndices);
                vector_object_VectorIndices_all = catmethod(...
                    vector_object_VectorIndices_all,vector_object_VectorIndices);
            end
            
            one_d_VectorIndices_all = reshape(VectorIndices_all,numel(VectorIndices_all),1);
            one_d_vector_object_VectorIndices_all = ...
                reshape(vector_object_VectorIndices_all,...
                numel(vector_object_VectorIndices_all),1);
            
            vectorObject = varargin{1}.VectorObject.concat(...
                vectorObjects,one_d_vector_object_VectorIndices_all,one_d_VectorIndices_all);
            
            VectorIndices = 0:numel(VectorIndices_all)-1;
            VectorIndices = reshape(VectorIndices,size(VectorIndices_all));
            
            x = obj.createObject(vectorObject,VectorIndices);
        end
    end
    
    methods (Access=protected)
        function v = unpack(obj,stuff,returnSingletonIfOnlyOne)
            if nargin < 3
                returnSingletonIfOnlyOne = false;
            end
            
            if iscell(stuff)
                if numel(obj.VectorIndices) == 1
                    v = reshape(stuff,numel(stuff),1);
                    if returnSingletonIfOnlyOne
                        v = v{1};
                    end
                else
                    if size(stuff,1) ~= numel(obj.VectorIndices)
                        error('mismatch of sizes');
                    end
                    stuff = stuff(obj.VectorIndices(:)+1,:);
                    v = reshape(stuff,size(obj.VectorIndices));
                    if numel(v) == 1 && returnSingletonIfOnlyOne
                        v = v{1};
                    end
                end
                
            else
                
                if numel(obj.VectorIndices) == 1
                    v = reshape(stuff,numel(stuff),1);
                else
                    if size(stuff,1) ~= numel(obj.VectorIndices)
                        error('mismatch of sizes');
                    end
                    stuff = stuff(obj.VectorIndices(:)+1,:);
                    v = reshape(stuff,[size(obj.VectorIndices) size(stuff,2)]);
                end
            end
        end
        
        function v = pack(obj,values)
            numValsPerObj = numel(values) / numel(obj.VectorIndices);
            if mod(numValsPerObj,1) ~= 0
                error('invalid number of values');
            end
            
            v = reshape(values,numel(values)/numValsPerObj,numValsPerObj);
            v = v(obj.VectorIndices+1,:);
        end
    end
    
    methods (Abstract, Access = protected)
        retval = createObject(obj,vectorObject,VectorIndices);
        verifyCanConcatenate(obj,otherObjects);
    end
    
    
end