function [ Matchgewichte ] = Matchingweights3( Nodes, Changes, giveback,period )
%Wir werden hier pr�fen, wieviel sich aus der aktuellen Situation rausholen
%l�sst:
%Wir bestimmen also f�r 2 Linien die minimale Wartezeit, wenn der Versatz
%optimal ist und die maximale Wartezeit, also den worst case
%Die Differenz ist dann unser indikator
Matchgewichte=sparse(size(giveback,1)-1,size(giveback,1)-1);

for i=1:size(Nodes,2)-1
    for j=i+1:size(Nodes,2)
        RechnungsdatenAB=[];
        RechnungsdatenBA=[];
        for m=1:size(Nodes{i})
            for n=1:size(Nodes{j})
                LineA= Nodes{i}(m);
                LineB= Nodes{j}(n);
                [ ChangeAB, ChangeBA ] = Datensammler( LineA, LineB, Changes, giveback);
                %DatenAB ist eie Zeile aus Changes mit der 1x11 Struktur
                %
                RechnungsdatenAB=[RechnungsdatenAB ; ChangeAB];
                RechnungsdatenBA=[RechnungsdatenBA ; ChangeBA];
                
            end
        end
        
        if(~isempty(RechnungsdatenAB) || ~isempty(RechnungsdatenBA))
            
            
            [ ~, Wartezeit ,~] = Linienversatz(  RechnungsdatenAB, RechnungsdatenBA ,period);
            [ WartezeitWORST] = LinienversatzWORST(  RechnungsdatenAB, RechnungsdatenBA ,period);
            
            Differenz=sum(WartezeitWORST)-sum(Wartezeit);
            
            Matchgewichte(LineA, LineB)=Differenz; %Hier sammeln wir unterm Strich das gesamte Kantengewicht zwischen den zur Zeit betrachteten Knoten i und j
        end
        
        
    end
end



end

