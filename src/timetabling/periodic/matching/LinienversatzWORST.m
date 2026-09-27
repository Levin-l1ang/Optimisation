function [ Wartezeit ] = LinienversatzWORST( RechnungsdatenAB, RechnungsdatenBA,period )
%Wir bekommen von zwei Knoten die Eckdaten der Personen die von Knoten A
%nach B bzw von B nach A wechseln wollen.


        %Nun bestimmen wir den  SCHLECHTESTEN Versatz zwischen zwei Linien
        
        
        Wartezeit=zeros(size(RechnungsdatenAB,1)+size(RechnungsdatenBA,1),1);
       % Versatz=0;
        for j=0:(period-1)
            %timeB-timeA mod freqB
            if(~isempty(RechnungsdatenAB)) %Also tats�chlich jmd von A nach B umsteigen will
                WartAB=mod(RechnungsdatenAB(:,11)+j,period).*RechnungsdatenAB(:,6);
            else
                WartAB =0;
            end
            
            %timeA-timeB mod freqA
            if(~isempty(RechnungsdatenBA)) %Also tats�chlich jmd von B nach A umsteigen will
                WartBA=mod(RechnungsdatenBA(:,11)-j,period).*RechnungsdatenBA(:,6);
            else
                WartBA =0;
            end
            %Graph=[Graph; j (sum(WartAB)+sum(WartBA))];
            
            if ((sum(WartAB)+sum(WartBA))>sum(Wartezeit)) %%Hier steckt der unterschied im ">" also falls die gefunden Wartezeit gr��er ist als die alte!
                Wartezeit=[WartAB ; WartBA];
                sum(Wartezeit);
              %  Versatz=j;
            end
            
        end


end



