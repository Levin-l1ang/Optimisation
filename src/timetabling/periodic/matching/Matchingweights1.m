function [ Umstieg ] = Matchingweights1( Umstieg )
%Aus der Umstiegsmatrix an Fahrgästen produzieren wir lediglich eine obere
%Dreiecksmatrix, das heißt es gibt keinen Unterschied mehr ob von i nach j
%oder von j nach i umgestiegen wird.

Umstieg=triu(Umstieg)+triu(Umstieg');
end

