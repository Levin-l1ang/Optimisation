function [ Matchgewichte ] = Matchingweights2( Nodes, Changes, giveback ,period)
%Wir wollen nun jeden Knoten mit jedem Knoten vergleichen und bestimmen
%welche Wartezeiten beim abgleichen zu erwarten sind, kleine Werte lasse
%darauf schlie�en, dass sie sich gut abgleichen lassen



%ACHTUNG: Kleinste Wartezeiten brauchen dennoch das gr��te Gewicht => suche
%am Ende das Maximum, inkrementiere es um 1 und ziehe alle Werte von diesem
%Maximum ab => kleinste Wartezeit bekommt gr��ten Wert.

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
         %   Wir Manipulieren kurz die Rechnungsdaten, indem wir
         %   die Anzahl der Passagiere auf 1 setzen, Stichwort Normierung
         %   f�r \tilde{C}
            if(~isempty(RechnungsdatenAB))
                RechnungsdatenAB(:,6)=1 ;
            end
            if(~isempty(RechnungsdatenBA))
                RechnungsdatenBA(:,6)=1 ;
            end
            
            [ ~, Wartezeit ,~] = Linienversatz(  RechnungsdatenAB, RechnungsdatenBA,period );
        
            Wartezeit=sum(Wartezeit);
            if(Wartezeit==0)%also sich Z�ge perfekt aufeinander abstimmen lassen addieren wir ein epsilon um sie von der initialisiert Null zu unterscheiden
                Wartezeit=0.5; %Es gen�gt wahrscheinlich ein +0.5 weil die Wartezeiten in ganzen Minuten angegeben werden, also kein kleineres epsilon
            end
            Matchgewichte(LineA, LineB)=Wartezeit; %Hier sammeln wir unterm Strich das gesamte Kantengewicht zwischen den zur Zeit betrachteten Knoten i und j
        end
        
        
    end
end



if(max(Matchgewichte(:))<10)
    Matchgewichte(find(Matchgewichte))=10-Matchgewichte(find(Matchgewichte));
else
    Matchgewichte(find(Matchgewichte))=max(Matchgewichte(:))+1-Matchgewichte(find(Matchgewichte));
end




end

