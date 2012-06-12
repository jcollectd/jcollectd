/*
 * jcollectd
 * Copyright (C) 2009 Hyperic, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; only version 2 of the License is applicable.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA  02110-1301 USA
 */

package org.collectd.agent.protocol;

import org.collectd.agent.api.DataSource;
import org.collectd.agent.api.DataSet;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Parser for collectd/src/types.db format.
 */
public class TypesDB {

    //List<Type> == plugin.h:data_set_t
    private final Map<String, List<DataSource>> _types =
            new HashMap<String, List<DataSource>>();

    private static TypesDB _instance;

    public Map<String, List<DataSource>> getTypes() {
        return _types;
    }

    public List<DataSource> getType(String name) {
        return _types.get(name);
    }

    public static synchronized TypesDB getInstance() {
        if (_instance == null) {
            _instance = new TypesDB();
            try {
                _instance.load();
                _instance.load(Network.getProperty("typesdb"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return _instance;
    }

    void load(String files) throws IOException {
        if (files == null) {
            return;
        }
        StringTokenizer tok = new StringTokenizer(files, File.pathSeparator);
        while (tok.hasMoreTokens()) {
            File file = new File(tok.nextToken());
            if (file.exists()) {
                load(file);
            }
        }
    }

    public void load() throws IOException {
        InputStream is =
                getClass().getClassLoader().getResourceAsStream("META-INF/types.db");
        try {
            load(is);
        } finally {
            is.close();
        }
    }

    //collectd/src/types_list.h:read_types_list
    void load(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        try {
            load(is);
        } finally {
            is.close();
        }
    }

    void load(InputStream is) throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(is));

        String line;

        while ((line = reader.readLine()) != null) {
            DataSet ds;

            ds = DataSet.parseDataSet(line);
            if (ds != null) {
                String type = ds.getType();
                List<DataSource> dsrc = ds.getDataSources();

                this._types.put(type, dsrc);
            }
        }
    } /* void load */

    public static void main(String[] args) throws Exception {
        TypesDB tl = new TypesDB();
        if (args.length == 0) {
            tl.load();
        } else {
            for (String arg : args) {
                tl.load(new File(arg));
            }
        }
        Map<String, List<DataSource>> types = tl.getTypes();
        for (Map.Entry<String, List<DataSource>> entry : types.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }
}
