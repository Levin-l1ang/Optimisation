function [ timetable ] = begin4( matchverfahren,matchweight, Event,Changes,lower_bound_change_edges,period)
%Die Zusammenf�hrung und Auswertung aller Routinen findet in diesem
%Programm statt, quasi das Herz der Arbeit
%Eingabe: Verfahrens- und Gewichtswahl, Daten des EAN

%Ausgabe: periodischer Fahrplan 






%%
Umstieg=Umsteigermatrix(Event, Changes);

%%
%Unsere Knoten representieren wir durch ein Cellarray mit Spaltenvektoren
%als Elementen
%Erste initialisierung: jeder Knoten entspricht genau einer Linie:

Lines=unique(Event(:,3));
Nodes=cell(1,size(Lines,1));
for i=1:size(Lines,1)
    Nodes{i}=[Lines(i)];
end

%%
%Hier initialiseren wir den R�ckgabewert:
%Es wird ein Vektor mit allen Versatzzeiten und der Summe aller Wartezeiten
giveback=zeros(size(Lines,1)+1,1);

%%
%Bestimme nun welche Knoten gematched werden sollen:
%Dazu erzeugen wir uns insbesondere eine Matrix, welcher wir die Kantengewichte f�r unseres
%Matchingverfahren ablesen k�nnen:
switch matchweight
    case 1
        Match=Matchingweights1(Umstieg);%Gibt eine Matrix mit Kantengewichten f�r das Matchingverfahren zur�ck
        
       
        
    case 2
        Match=Matchingweights2(Nodes, Changes, giveback,period);
   
      
    case 3
        Match=Matchingweights3(Nodes, Changes, giveback,period);
        
        
end
%Match
%size(find(Match))
%size(unique([Changes(:,7);Changes(:,9)]))

disp('Init waiting time!')
Wait=Wartezeit2(Changes, giveback,period);


%%
GoOn = 1; %Diese Kontrollvariable wird ver�ndert falls wird nicht weiter matchen k�nnen

while(GoOn ==1)
    %for bob=1:10
    switch matchverfahren
        case 1
            combine=greedy(Nodes, Match);
        case 2
            combine=matchgreedy(Nodes, Match);
        case 3
            combine=matchperfect(Nodes, Match);
    end
    
    
    %grobe Idee dazu: Die Vektoren combine(i,1) und combine(i,2) bilden den Index
    %unserer Matchingedges, und wenn wir dann einen "nicht-null"-Eintrag
    %finden, stellen wir eine Gleichung auf
    
    %Insbesondere k�nnen wir �ber die i x 2 Struktur von Combine mehrere Knoten
    %verschmelzen
    
    for i=1:size(combine,1)
  
        %Wir bekommen von unserem Matchverfahren NaN als R�ckgabe falls sich nichts
        %mehr matchen l��t:
        if(isnan(combine(1)) || isnan(combine(2)))
            %Also ver�ndern wir unsere Kontrollvariable und brechen das for
            %ab
            GoOn=0;
            break
        end
        %%Ab hier stellen wir die Gleichungen auf
        RechnungsdatenAB=[];
        RechnungsdatenBA=[];
        for m=1:size(Nodes{combine(i,1)},1)
            for n=1:size(Nodes{combine(i,2)},1)
                
                LineA=Nodes{combine(i,1)}(m);
                LineB=Nodes{combine(i,2)}(n);
                
                [DatenAB, DatenBA] = Datensammler(LineA, LineB, Changes, giveback);
                
                RechnungsdatenAB=[ RechnungsdatenAB; DatenAB];
                RechnungsdatenBA=[ RechnungsdatenBA; DatenBA];
                
                
            end%Nodes(combine(2))
        end%Nodes(combine(1))
    
        
        
        [Versatz, Wartezeit, Graph]=Linienversatz( RechnungsdatenAB, RechnungsdatenBA,period);
        
        
        
  
        %         disp('Die Linien')
        %         Nodes{combine(i,1)}
        %         disp('und')
        %         Nodes{combine(i,2)}
        %         sprintf('sollten um %i Minuten versetzt fahren mit Wartezeit' , Versatz )
  
        
        %Hier tragen wir unsere Daten in die R�ckgabevariable ein
        %Beachte, der Versatz sagt aus, das die B Linie(n) "Versatz"
        %Minuten nach der/n A Linie(n) fahren sollen
        giveback(Nodes{combine(i,2)})=giveback(Nodes{combine(i,2)})+Versatz;
        giveback(end)=giveback(end)+sum(Wartezeit);
        
   
    end
    %Hier werden wir die aktuelle Wartezeitfunktion W(x) auswerten

    Wait=[Wait Wartezeit2(Changes, giveback,period)];
    
    
    if(GoOn==0)%wenn sich nichts mehr matchen l��t, landen wir hier und beenden die while schleife
        break
    end
    Nodes=NodeMelt(Nodes, combine); %Hier vereinigen wir die Knoten des Matching graphen
    %return %um das while auszuhebeln
    
    %Da sich nun Knoten vereinigt und bereits Paare und Versatz gebildet
    %haben, ist es n�tig die Kantengewichte neu zu bestimmen:
  
    switch matchweight
        case 1
            Match=Matchingweights1(Umstieg) ;%Gibt eine Matrix mit Kantengewichten f�r das Matchingverfahren zur�ck
            
            
        case 2
            Match=Matchingweights2(Nodes, Changes, giveback,period);
            
        case 3
            Match=Matchingweights3(Nodes, Changes, giveback,period);
            
    end
    
    
end

%%
disp('Finishing calculation for timetable!')



giveback=mod(giveback,period);
for i=1:size(giveback,1)-1
    index_line_i=find(Event(:,3)==i);
    Event(index_line_i,5)=Event(index_line_i,5)+giveback(i);
end


timetable=[Event(:,1) Event(:,5)];
return;
end

