import { NavLink } from "react-router";
import { motion } from "motion/react";
import { MdArrowBack } from "react-icons/md";

const NotFound: React.FC = () => {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-background-dark px-4">
      <div className="text-center max-w-md">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", stiffness: 200, damping: 15 }}
          className="relative mb-8"
        >
          <span className="text-[10rem] md:text-[12rem] font-extrabold leading-none bg-clip-text text-transparent bg-linear-to-r from-primary to-secondary select-none">
            404
          </span>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: [0, 0.6, 0.6] }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className="absolute inset-0 bg-linear-to-r from-primary/20 to-secondary/20 blur-3xl"
            aria-hidden="true"
          />
        </motion.div>

        <motion.h1
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.2, duration: 0.5 }}
          className="text-2xl md:text-3xl font-bold text-text-main mb-4"
        >
          Page not found
        </motion.h1>

        <motion.p
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.3, duration: 0.5 }}
          className="text-text-muted mb-8 leading-relaxed"
        >
          The page you're looking for doesn't exist or has been moved.
        </motion.p>

        <motion.div
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.4, duration: 0.5 }}
        >
          <NavLink
            to="/"
            className="group inline-flex items-center gap-2 bg-primary hover:bg-primary-dark text-white font-semibold py-3 px-6 rounded-lg shadow-lg shadow-primary/25 transition-all"
          >
            <MdArrowBack className="text-lg transition-transform duration-200 group-hover:-translate-x-1" />
            <span>Back to home</span>
          </NavLink>
        </motion.div>
      </div>
    </div>
  );
};

export default NotFound;
