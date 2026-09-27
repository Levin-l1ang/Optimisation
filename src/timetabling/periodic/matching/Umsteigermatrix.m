function [ Umstieg ] = Umsteigermatrix( Event, Changes)
%Die Matrix Umstieg enthält die Summer aller Umsteigenden Personen zwischen
%2 Linien, Im Eintrag i,j stehen also alle Passagiere die von i nach j
%wechseln wollen, aber nicht diejenigen welche von j nach i wechseln.


% Changes := [ActivityID  tailEventId headEventId lowerBound upperBound passengers 
%               1           2           3           4           5        6          
%In Changes spalte 7 und 8 stehen somit die Linien und Arrival Zeiten "i"
%In Changes spalte 9 und 10 stehen somit die Linien und Departure Zeiten "j"
%Die sich somit ergebende Rechenkonstante ist in Changes (:,11)

Lines=unique(Event(:,3));
Umstieg=sparse(size(Lines,1),size(Lines,1));




for i=1:size(Changes,1)
   

       Umstieg(Changes(i,7),Changes(i,9))=Umstieg(Changes(i,7),Changes(i,9))+Changes(i,6);
   
end

return
end

