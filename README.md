# Devoir 1 — Compression de fichiers par codage de Huffman (Java, sans bibliothèque externe)

Seul le JDK standard est utilisé (`java.io`, `java.nio.file`). Même la file de priorité
est réécrite à la main (`MinHeap`), et les tests n'utilisent pas JUnit.

## 1. Lancer le projet dans VS Code

Prérequis : JDK 17 ou plus récent + l'extension **Extension Pack for Java** (Microsoft).

1. `Fichier > Ouvrir le dossier…` → choisir le dossier `huffman`.
2. Onglet **Exécuter et déboguer** (Ctrl+Shift+D) → choisir une configuration :
   - *Tests Huffman* : 14 tests automatiques ;
   - *Compresser / Décompresser / Statistiques* sur `exemples/texte.txt`.
3. Ou, dans le terminal intégré :

```bash
./build.sh            # Linux / macOS  (build.bat sous Windows)

java -cp bin huffman.Main compress   exemples/texte.txt  texte.huf
java -cp bin huffman.Main decompress texte.huf           texte_restaure.txt
java -cp bin huffman.Main stats      exemples/texte.txt
```

## 2. Organisation du code

| Fichier | Rôle |
|---|---|
| `Node.java` | Nœud de l'arbre (feuille = symbole, nœud interne = deux fils) |
| `MinHeap.java` | Tas binaire minimum écrit à la main |
| `HuffmanCode.java` | Fréquences → arbre → longueurs → **codes canoniques** ; décodage |
| `BitOutputStream.java` / `BitInputStream.java` | Lecture/écriture bit à bit |
| `HuffmanCompressor.java` | Format du fichier `.huf`, compression et décompression |
| `Main.java` | Ligne de commande (`compress`, `decompress`, `stats`) |
| `test/.../HuffmanTests.java` | 14 tests : cas limites, propriétés théoriques, robustesse |

## 3. Le principe

Un fichier est une suite d'octets ; chacun est normalement stocké sur 8 bits.
Huffman remplace ce code de **longueur fixe** par un code de **longueur variable** :
les octets fréquents reçoivent des codes courts, les rares des codes longs.

Pour pouvoir décoder sans séparateur, le code doit être **préfixe** : aucun mot de code
n'est le début d'un autre. Un code préfixe binaire, c'est exactement un arbre binaire dont
les symboles sont les feuilles : on lit 0 → à gauche, 1 → à droite, jusqu'à une feuille.

### L'algorithme (glouton)

```
mettre une feuille (symbole, fréquence) par symbole présent dans un tas min
tant que le tas contient plus d'un arbre :
    x ← extraire le minimum
    y ← extraire le minimum
    insérer un nœud de poids x.poids + y.poids, de fils x et y
la racine restante est l'arbre de Huffman
```

La longueur du code d'un symbole = la profondeur de sa feuille.

### Exemple : `ABRACADABRA` (A:5, B:2, R:2, C:1, D:1)

```
étape 1 : C(1) + D(1)        → CD(2)
étape 2 : B(2) + CD(2)       → BCD(4)
étape 3 : R(2) + BCD(4)      → RBCD(6)
étape 4 : A(5) + RBCD(6)     → racine(11)
```

| Symbole | Fréq. | Longueur | Code canonique |
|---|---|---|---|
| A | 5 | 1 | `0` |
| R | 2 | 2 | `10` |
| B | 2 | 3 | `110` |
| C | 1 | 4 | `1110` |
| D | 1 | 4 | `1111` |

Coût : 5·1 + 2·2 + 2·3 + 1·4 + 1·4 = **23 bits** au lieu de 11 × 8 = 88 bits.
(Vérifiable : `java -cp bin huffman.Main stats` sur un fichier contenant ABRACADABRA.)

## 4. Pourquoi c'est optimal (à savoir expliquer)

On minimise `L = Σ p(s) · l(s)` sur tous les codes préfixes. La preuve repose sur deux lemmes.

1. **Choix glouton.** Il existe un arbre optimal où les deux symboles les moins fréquents
   x et y sont frères, à la profondeur maximale. *Idée :* dans un arbre optimal, prenons deux
   feuilles sœurs a et b au niveau le plus profond. Échanger a avec x ne peut pas augmenter
   le coût, car p(x) ≤ p(a) et prof(x) ≤ prof(a) : la variation vaut
   (p(a) − p(x))·(prof(x) − prof(a)) ≤ 0. Idem pour b et y.
2. **Sous-structure optimale.** Si l'on remplace x et y par un symbole fictif z de fréquence
   p(x)+p(y), alors coût(T) = coût(T') + p(x) + p(y). Un arbre optimal pour l'alphabet réduit
   donne donc un arbre optimal pour l'alphabet complet.

Par récurrence sur le nombre de symboles, l'algorithme est optimal **parmi les codes préfixes
qui codent chaque symbole séparément**.

**Borne de Shannon.** Avec l'entropie `H = −Σ p(s) log₂ p(s)`, on a toujours
`H ≤ L < H + 1`. Le test `borne de Shannon` le vérifie sur 50 distributions aléatoires,
et la commande `stats` affiche H et L pour n'importe quel fichier.

**Inégalité de Kraft.** Un code préfixe vérifie `Σ 2^(−l(s)) ≤ 1` ; un arbre de Huffman étant
complet (tout nœud interne a deux fils), on a l'égalité. Le décompresseur vérifie Kraft pour
rejeter un en-tête corrompu.

## 5. Le code canonique (le choix technique le plus important)

Le décompresseur doit connaître le code utilisé. Deux options :

- transmettre l'arbre ou les fréquences (lourd, et dépend de la façon de construire l'arbre) ;
- **transmettre seulement la longueur de chaque code** — c'est ce que font DEFLATE (zip, gzip, PNG) et JPEG.

À longueurs fixées, on attribue les codes de façon déterministe :

```
trier les symboles par (longueur, valeur du symbole)
code ← 0
pour chaque longueur L = 1 .. max :
    pour chaque symbole de longueur L, dans l'ordre :
        code(symbole) ← code ;  code ← code + 1
    code ← code << 1
```

Conséquences :
- l'en-tête tient en **256 octets** (une longueur par octet possible) ;
- les codes d'une même longueur sont **consécutifs**, ce qui permet de décoder **sans arbre** :
  après avoir lu L bits formant la valeur v, si `0 ≤ v − premierCode[L] < nombre[L]`,
  le symbole est `symbolesTriés[premierIndex[L] + v − premierCode[L]]` (voir `decodeSymbol`).

Les codes sont rangés dans un `long`, donc limités à 63 bits. Ce n'est pas une limite en
pratique : une profondeur d nécessite au moins Fib(d+2) octets dans le fichier
(cas des fréquences de Fibonacci, testé), soit plus de 17 000 milliards d'octets pour d = 64.

## 6. Format du fichier `.huf`

```
"HUF1"  (4 octets)  signature, pour refuser un fichier qui n'a pas été compressé
taille  (8 octets)  nombre d'octets du fichier original
L[0..255] (256 octets) longueur du code de chaque octet (0 = absent)
données : codes concaténés, dernier octet complété par des 0
```

En-tête total : **268 octets**. La taille originale dit au décompresseur où s'arrêter :
les bits de bourrage ne sont jamais décodés comme des symboles.

## 7. Points délicats traités

| Situation | Traitement |
|---|---|
| Fichier vide | En-tête seul, aucune donnée |
| Un seul symbole distinct (`aaaa…`) | L'arbre serait une feuille de profondeur 0 → on impose un code de 1 bit |
| `byte` Java signé (−128..127) | `b & 0xFF` pour obtenir l'indice 0..255 |
| Égalités de poids dans le tas | Départage par le plus petit symbole → résultat déterministe |
| Arbre très profond | Parcours itératif (pile explicite), pas de récursion |
| Fichier tronqué ou corrompu | `IOException` claire (signature, Kraft, fin de flux) |
| Gros fichiers | Lecture en deux passes par flux tamponnés, mémoire constante |

## 8. Complexité

- Comptage des fréquences : O(n), n = taille du fichier.
- Construction de l'arbre : O(k log k), k ≤ 256 symboles distincts (donc négligeable).
- Codage / décodage : O(n · L̄), L̄ = longueur moyenne d'un code.
- Mémoire : O(k), indépendante de la taille du fichier.

## 9. Limites (à mentionner avant que le jury ne les relève)

- **Deux passes** sur le fichier (impossible sur un flux qu'on ne peut relire). Alternative : Huffman adaptatif (algorithme FGK / Vitter).
- **Au moins 1 bit par symbole** : sur une source très redondante, Huffman reste loin de l'entropie ; le codage arithmétique fait mieux.
- **Aucun gain** sur des données déjà compressées ou aléatoires (JPEG, ZIP, MP4) : le fichier grossit de 268 octets.
- Huffman ne voit que la fréquence de chaque octet, **pas les répétitions** (« abcabcabc »). C'est pourquoi DEFLATE combine LZ77 (répétitions) + Huffman.

## 10. Questions probables à l'oral

- *Pourquoi un code préfixe ?* — Pour décoder sans séparateur ni ambiguïté.
- *Pourquoi un tas ?* — Pour extraire les deux minima en O(log k) au lieu de O(k).
- *Huffman est-il unique ?* — Non : les égalités et le choix gauche/droite donnent plusieurs arbres, tous de même coût. Le code canonique supprime cette ambiguïté.
- *Que contient l'en-tête et pourquoi pas l'arbre ?* — 256 longueurs ; le code canonique permet au décodeur de reconstruire exactement les mêmes codes.
- *Pourquoi stocker la taille originale ?* — Pour ne pas décoder les bits de bourrage du dernier octet.
- *Comment prouvez-vous que ça marche ?* — Aller-retour octet par octet sur 14 jeux de données, y compris les cas limites, et vérification des propriétés théoriques (préfixe, Kraft, Shannon, valeur 23 bits d'ABRACADABRA).
