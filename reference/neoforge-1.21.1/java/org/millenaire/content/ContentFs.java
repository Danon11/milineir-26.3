package org.millenaire.content;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface ContentFs {
   Optional<Resource> findFirst(String var1);

   List<Resource> findAll(String var1);

   Stream<Resource> walk(String var1, int var2);

   ContentFs sub(String var1);
}
